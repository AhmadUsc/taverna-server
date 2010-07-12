package org.taverna.server.localworker.impl;

import static java.lang.Runtime.getRuntime;
import static java.lang.System.exit;
import static java.lang.System.out;
import static java.lang.System.setProperty;
import static java.lang.System.setSecurityManager;
import static java.rmi.registry.LocateRegistry.getRegistry;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.Principal;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.taverna.server.localworker.remote.RemoteRunFactory;
import org.taverna.server.localworker.remote.RemoteSingleRun;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

/**
 * The registered factory for runs, this class is responsible for constructing
 * runs that are suitable for particular users. It is also the entry point for
 * this whole process.
 * 
 * @author Donal Fellows
 * @see LocalWorker
 */
public class TavernaRunManager extends UnicastRemoteObject implements
		RemoteRunFactory {
	DocumentBuilderFactory dbf;
	TransformerFactory tf;
	String command;
	Constructor<? extends RemoteSingleRun> cons;
	Class<? extends Worker> workerClass;

	/**
	 * How to get the actual workflow document from the XML document that it is
	 * contained in.
	 * 
	 * @param containerDocument
	 *            The document sent from the web interface.
	 * @return The element describing the workflow, as expected by the Taverna
	 *         command line executor.
	 */
	protected Element unwrapWorkflow(Document containerDocument) {
		return (Element) containerDocument.getDocumentElement().getFirstChild();
	}

	private static final String usage = "java -jar server.worker.jar workflowExecScript UUID";

	/**
	 * An RMI-enabled factory for runs.
	 * 
	 * @param command
	 *            What command to call to actually run a run.
	 * @param constructor
	 *            What constructor to call to instantiate the RMI server object
	 *            for the run. The constructor <i>must</i> be able to take two
	 *            strings (the execution command, and the SCUFL document) and a
	 *            class (the <tt>workerClass</tt> parameter, below) as
	 *            arguments.
	 * @param workerClass
	 *            What class to create to actually manufacture and manage the
	 *            connection to the workflow engine.
	 * @throws RemoteException
	 *             If anything goes wrong during creation of the instance.
	 */
	public TavernaRunManager(String command,
			Constructor<? extends RemoteSingleRun> constructor,
			Class<? extends Worker> workerClass) throws RemoteException {
		this.command = command;
		this.dbf = DocumentBuilderFactory.newInstance();
		this.dbf.setNamespaceAware(true);
		this.dbf.setCoalescing(true);
		this.tf = TransformerFactory.newInstance();
		this.cons = constructor;
		this.workerClass = workerClass;
	}

	@Override
	public RemoteSingleRun make(String scufl, Principal creator)
			throws RemoteException {
		StringReader sr = new StringReader(scufl);
		StringWriter sw = new StringWriter();
		try {
			tf.newTransformer()
					.transform(
							new DOMSource(unwrapWorkflow(dbf
									.newDocumentBuilder().parse(
											new InputSource(sr)))),
							new StreamResult(sw));
		} catch (Exception e) {
			throw new RemoteException("failed to extract contained workflow", e);
		}
		try {
			// TODO: Do something properly with creator
			out.println("Creating run for "
					+ (creator == null ? "<NOBODY>" : creator.getName()));
			return cons.newInstance(command, sw.toString(), workerClass);
		} catch (InvocationTargetException e) {
			if (e.getTargetException() instanceof RemoteException)
				throw (RemoteException) e.getTargetException();
			throw new RemoteException("unexpected exception", e
					.getTargetException());
		} catch (Exception e) {
			throw new RemoteException("bad instance construction", e);
		}
	}

	private static boolean shuttingDown;
	private static String factoryName;
	private static Registry registry;

	static synchronized void unregisterFactory() {
		if (!shuttingDown) {
			shuttingDown = true;
			try {
				if (factoryName != null && registry != null)
					registry.unbind(factoryName);
			} catch (Exception e) {
				e.printStackTrace(out);
			}
		}
	}

	@Override
	public void shutdown() {
		unregisterFactory();
		new Thread() {
			@Override
			public void run() {
				try {
					sleep(1000);
				} catch (InterruptedException e) {
				} finally {
					exit(0);
				}
			}
		}.start();
	}

	/**
	 * The name of the file (in this code's resources) that provides the default
	 * security policy that we use.
	 */
	public static final String SECURITY_POLICY_FILE = "security.policy";

	/**
	 * @param args
	 *            The arguments from the command line invocation.
	 * @throws Exception
	 *             If we can't connect to the RMI registry, or if we can't read
	 *             the workflow, or if we can't build the worker instance, or
	 *             register it. Also if the arguments are wrong.
	 */
	public static void main(String[] args) throws Exception {
		if (args.length != 2)
			throw new Exception("wrong # args: must be \"" + usage + "\"");
		setProperty("java.security.policy", LocalWorker.class.getClassLoader()
				.getResource(SECURITY_POLICY_FILE).toExternalForm());
		setSecurityManager(new RMISecurityManager());
		String command = args[0];
		factoryName = args[1];
		registry = getRegistry();
		registry.bind(factoryName, new TavernaRunManager(command,
				LocalWorker.class.getDeclaredConstructor(String.class,
						String.class, Class.class), WorkerCore.class));
		getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				unregisterFactory();
			}
		});
		out.println("registered RemoteRunFactory with ID " + factoryName);
	}
}