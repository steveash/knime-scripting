package de.mpicbg.knime.scripting.matlab.srv;

import gnu.cajo.invoke.Remote;
import gnu.cajo.utils.ItemServer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import matlabcontrol.MatlabConnectionException;
import matlabcontrol.MatlabInvocationException;
import matlabcontrol.MatlabProxy;



/**
 * This is the MATLAB server for the KNIME scripting integration.
 * It can be run on a remote machine where the KNIME clients access it
 * using JVM-to-JVM communication with the cajo library
 *  
 * @author Holger Brandl, Tom Haux, Felix Meyenhofer
 */
public class MatlabServer implements MatlabRemote {
	
	/** Hash to maintain the file map */
	private ServerFileMap fileMap = new ServerFileMap();
	
	/** MATLAB controller object */
	private MatlabController matlabController;
	
	/** MATLAB proxy holder */
	private ArrayList<MatlabProxy> matlabProxyHolder = new ArrayList<MatlabProxy>(1);
	
	private int referenceNumber;
	
	
	/**
	 * Constructor
	 * 
	 * @param port
	 * @param sessions
	 */
	public MatlabServer(int port, int sessions) {
		try {
            System.out.println("Configuring on port: " + port);
            Remote.config(null, port, null, 0);
            
            System.out.println("Registering with name: " + REGISTRY_NAME);
            ItemServer.bind(this, REGISTRY_NAME);
            
//            matlabController = new MatlabController(sessions, true);
            this.matlabController = MatlabController.getInstance();
            this.referenceNumber = MatlabController.getReferenceCount();
            this.matlabController.setParameter(sessions, true);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to write registry.");
        } catch (MatlabConnectionException e) {
        	e.printStackTrace();
			throw new RuntimeException("Unable to connect to the MATLAB application.");
		}
	}
	
	
	/**
	 * Makes the MATLAB server runnable form the command line
	 * 
	 * @param args
	 */
	@SuppressWarnings("static-access")
	public static void main(String[] args) {
		 
		// Default values
		int port = MatlabRemote.DEFAULT_PORT;
		int threads = 1;
		
		// Create options
		Options options = new Options();
		options.addOption(new Option("h", "print help"));
		options.addOption(OptionBuilder.withArgName("port")
				.hasArg()
				.withDescription("server port")
				.create("p"));
		options.addOption(OptionBuilder.withArgName("threads")
				.hasArg()
				.withDescription("number of server threads (running MATLAB applications))")
				.create("t"));
		
		 // create the parser
	    CommandLineParser parser = new GnuParser();
	    try {
	        // parse the command line arguments
	        CommandLine line = parser.parse(options, args);
	        
	        if (line.hasOption("h")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("MatlabServer", options);
                System.exit(0);
            } else {
            	if (line.hasOption("p")) 
            		port = Integer.parseInt(line.getOptionValue("p"));
            	if(line.hasOption("t"))
            		threads = Integer.parseInt(line.getOptionValue("t"));
            }
	    }
	    catch( ParseException exp ) {
	        // oops, something went wrong
	        System.err.println( "Parsing failed.  Reason: " + exp.getMessage() );
	    }

		
	    // Welcome message
	    System.out.println("***************************************************************\n" +
	    		"      MATLAB server for the KNIME scripting integrations\n" +
	    		"***************************************************************");
		
	    // Start the server
	    new MatlabServer(port, threads);
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void acquireMatlabProxy() throws MatlabConnectionException {
		MatlabProxy proxy = matlabController.acquireProxyFromQueue();
		matlabProxyHolder.add(proxy);
		System.out.println("\tacquired contoller thread " + this.referenceNumber);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void releaseMatlabProxy() {
		if (this.matlabProxyHolder.size() > 0) {
			this.matlabController.returnProxyToQueue(this.matlabProxyHolder.remove(0));
			System.out.println("\treleased controller thread " + this.referenceNumber);
		}
	}
	
	/**
     * {@inheritDoc}
     */
	@Override
	public void printServerMessage(String msg) {
		System.out.println(msg);	
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public File createTempFile(String prefix, String suffix) throws IOException {
		File tempFile = File.createTempFile(prefix, suffix);
        tempFile.deleteOnExit();
        return tempFile;
    }
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getFilePath(File file) {
        return file.getAbsolutePath();
    }

	/**
	 * {@inheritDoc}
	 */
	@Override
    public boolean deleteFile(File file) {
        return file != null ? file.delete() : true;
    }

	/**
	 * {@inheritDoc}
	 */
	@Override
    public int openFile(File file) throws IOException {
        return fileMap.add(file);
    }

	/**
	 * {@inheritDoc}
	 */
	@Override
    public byte[] readFile(int descriptor) throws IOException {
        ServerFile file = fileMap.get(descriptor);
        return file.read();
    }

	/**
	 * {@inheritDoc}
	 */
	@Override
    public void writeFile(int descriptor, byte[] bytes) throws IOException {
        ServerFile file = fileMap.get(descriptor);
        file.write(bytes);
    }

	/**
	 * {@inheritDoc}
	 */
	@Override
    public void closeFile(int descriptor) throws IOException {
        ServerFile file = fileMap.get(descriptor);
        file.close();
        fileMap.remove(descriptor);
    }	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void eval(String cmd) throws MatlabInvocationException {
		if (matlabProxyHolder.size() == 0)
			throw new RuntimeException("Before doing MATLAB operations you have to call the acquireMatlabProxy method");
		
		matlabProxyHolder.get(0).eval(cmd);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void feval(String arg0, Object... arg1)
			throws MatlabInvocationException {
		throw new RuntimeException("The MATLAB server does currently not support the 'feval' operation.");
		
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getVariable(String cmd) throws MatlabInvocationException {
		if (matlabProxyHolder.size() == 0)
			throw new RuntimeException("Before doing MATLAB operations you have to call the acquireMatlabProxy method");
		
		return matlabProxyHolder.get(0).getVariable(cmd);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object[] returningEval(String arg0, int arg1)
			throws MatlabInvocationException {
		throw new RuntimeException("The MATLAB server does currently not support the 'feval' operation.");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object[] returningFeval(String arg0, int arg1, Object... arg2)
			throws MatlabInvocationException {
		throw new RuntimeException("The MATLAB server does currently not support the 'feval' operation.");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setVariable(String arg0, Object arg1)
			throws MatlabInvocationException {
		throw new RuntimeException("The MATLAB server does currently not support the 'feval' operation.");
	}

	

    /**
     * Maintain a map of descriptors to ServerFile references.
     */
    class ServerFileMap {

    	/** Current position in the file maps */
        private int currentDescriptor = 0;
        
        /** File map */
        private Hashtable<String, ServerFile> map = new Hashtable<String, ServerFile>();


        /**
         * Constructor of the file map
         * 
         * @param descriptor
         * @return
         */
        public ServerFile get(int descriptor) {
            return map.get(Integer.toString(descriptor));
        }

        /**
         * Add a file to the map
         * 
         * @param file
         * @return
         */
        public int add(File file) {
            return add(new ServerFile(file));
        }

        /**
         * Add a file to the map
         * 
         * @param file
         * @return
         */
        public int add(ServerFile file) {
            String descriptor = Integer.toString(currentDescriptor);
            map.put(descriptor, file);
            return currentDescriptor++;
        }

        /**
         * Remove a file form the map
         * 
         * @param descriptor
         */
        public void remove(int descriptor) {
            map.remove(Integer.toString(descriptor));
        }
    }


    /**
     * Wrapper for the File object with write and read methods
     */
    class ServerFile {

        /** This stream or the {@link ServerFile#output} will be created */
        private InputStream input;
        
        /** This OR the {@link ServerFile#input} */
        private OutputStream output;
        
        /** Buffer used for read operations */
        private byte[] buffer = new byte[8192];
        
        /** File object */
        private File file;


        /**
         * Constructor of the ServerFile
         * 
         * @param file
         */
        public ServerFile(File file) {
            this.file = file;
        }

        /**
         * Read the server file
         * 
         * @return
         * @throws IOException
         */
        public byte[] read() throws IOException {
            // Create the stream if this is the first file operation
            if (input == null)
            	input = new BufferedInputStream(new FileInputStream(file));

            int n = input.read(buffer);
            byte[] b = buffer;
            
            if (n == -1)
            	b = new byte[0];
            else if (n < buffer.length) 
            	b = Arrays.copyOf(buffer, n);

            return b;
        }

        /**
         * Write the server file
         * 
         * @param bytes
         * @throws IOException
         */
        public void write(byte[] bytes) throws IOException {
            // Create the stream if this is the first file operation
            if (output == null) 
            	output = new BufferedOutputStream(new FileOutputStream(file));
            output.write(bytes);
        }

        /**
         * Close the server file
         * 
         * @throws IOException
         */
        public void close() throws IOException {
            // Close whichever stream was created
            if (input != null) 
            	input.close();
            if (output != null) 
            	output.close();
        }
    }

    
}

