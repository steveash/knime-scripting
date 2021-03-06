package de.mpicbg.knime.scripting.matlab.open;

import de.mpicbg.knime.knutils.AbstractNodeModel;
import de.mpicbg.knime.scripting.matlab.MatlabScriptingBundleActivator;
import de.mpicbg.knime.scripting.matlab.prefs.MatlabPreferenceInitializer;
import de.mpicbg.knime.scripting.matlab.srv.MatlabClient;
import matlabcontrol.MatlabConnectionException;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.ExecutionContext;


/**
 * Node to open a KNIME table in MATLAB
 * 
 * @author Felix Meyenhofer
 */
public class OpenInMatlab extends AbstractNodeModel {
	
    /** Object to access the MATLAB session */
    private MatlabClient matlab;
    
    /** Preference pane */
    private IPreferenceStore preferences = MatlabScriptingBundleActivator.getDefault().getPreferenceStore();
    
    /** MATLAB type */
    private String type;
    
    /** Number of MATLAB application instances */ 
    private int sessions;
    
    /** KNIME-to-MATLAB data transfer method */
    private String transfer;
    

    /**
     * Constructor for the node model.
     */
    public OpenInMatlab() {
    	// Define the ports and use a hash-map for setting models 
        super(1, 0, true);
        
        // Add a property change listener that re-initializes the MATLAB client if the local flag changes.
        preferences.addPropertyChangeListener(new IPropertyChangeListener() {
        	int newSessions = preferences.getInt(MatlabPreferenceInitializer.MATLAB_SESSIONS);

        	@Override
        	public void propertyChange(PropertyChangeEvent event) {
        		String newValue = event.getNewValue().toString();

        		if (event.getProperty() == MatlabPreferenceInitializer.MATLAB_SESSIONS) {
        			newSessions = Integer.parseInt(newValue);
        			logger.info("MATLAB: server property (MATLAB_SESSIONS) was changed to " + newSessions + ". Re-initializing MATLAB client.");
        			return;
        		}

        		initializeMatlabClient(true, newSessions);
        	}
        });
    }   
    
    
    /**
     * Instantiate the MATLAB client
     * 
     * @param local
     * @param sessions
     */
    private void initializeMatlabClient(boolean local, int sessions) {
    	if (matlab == null) {
	    	try {
	    		logger.info("Connecting to local MATLAB application.");
				matlab = new MatlabClient(true, sessions);
				logger.warn("Switch to your MATLAB application. The data is loaded in the workspace now.");
			} catch (MatlabConnectionException e) {
				logger.error("MATLAB could not be started. You have to install MATLAB on you computer" +
						" to use KNIME's MATLAB scripting integration.");
				e.printStackTrace();
			}
    	}
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected BufferedDataTable[] execute(final BufferedDataTable[] inData, final ExecutionContext exec) throws Exception {
    	// Instantiate the local MATLAB server      
        sessions = preferences.getInt(MatlabPreferenceInitializer.MATLAB_SESSIONS);
        initializeMatlabClient(true, sessions);
    	
        // Get the table
        BufferedDataTable data = inData[0];
        
        // Get properties from the preference pane
        type = MatlabScriptingBundleActivator.getDefault()
        		.getPreferenceStore()
        		.getString(MatlabPreferenceInitializer.MATLAB_TYPE);
        sessions = MatlabScriptingBundleActivator.getDefault()
        		.getPreferenceStore()
        		.getInt(MatlabPreferenceInitializer.MATLAB_SESSIONS);
        transfer = MatlabScriptingBundleActivator.getDefault()
        		.getPreferenceStore()
        		.getString(MatlabPreferenceInitializer.MATLAB_TRANSFER_METHOD);
        
        try {               	
        	// Execute the command in MATLAB
        	matlab.client.openTask(data, this.type, this.transfer);
        	exec.checkCanceled();
        	
        	// Housekeeping
        	matlab.cleanup();

        } catch (Exception e) {
        	throw e;
    	} finally {
    		if (matlab != null)
    			this.matlab.rollback(); // Double check if the proxy was returned (in case of an Exception it will happen here)
    	}
        
        logger.info("The data is now loaded in MATLAB. Switch to the MATLAB command window.");

        return new BufferedDataTable[0];
    }

}