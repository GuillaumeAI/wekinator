/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package wekimini;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import static wekimini.DtwLearningManager.RunningState.NOT_RUNNING;
import wekimini.learning.dtw.DtwData;
import wekimini.learning.dtw.DtwExample;
import wekimini.learning.dtw.DtwModel;
import wekimini.learning.dtw.DtwSettings;
import wekimini.osc.OSCDtwOutput;
import wekimini.osc.OSCOutputGroup;

/**
 *
 * @author rebecca
 */
public class DtwLearningManager implements ConnectsInputsToOutputs {

    private DtwModel model; // In future could make more than 1 model
    private transient final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
    private final Wekinator w;
    private boolean canRun = false;
    public static final String PROP_CANRUN = "canRun";
    private RunningState runningState = NOT_RUNNING;
    public static final String PROP_RUNNING_STATE = "runningState";
    public static final String PROP_HAS_EXAMPLES = "hasExamples";
    private boolean hasExamples = false;
    private DtwExample lastExample = null;
    private int lastExampleCategory = 0;

    public DtwLearningManager(Wekinator w, OSCOutputGroup group) {
        this.w = w;
        if (group.getNumOutputs() != 1) {
            throw new IllegalArgumentException("Only one DTW output allowed right now");
        }

        OSCDtwOutput out = (OSCDtwOutput) group.getOutput(0);
        int numGestures = out.getNumGestures();
        model = new DtwModel(out.getName(), out, numGestures, w, this, new DtwSettings());
        
        model.addDtwUpdateListener(new DtwModel.DtwUpdateListener1() {

            @Override
            public void dtwUpdateReceived(double[] currentDistances) {
                sendModelUpdates(currentDistances);
            }

        });
        model.addPropertyChangeListener(new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(DtwModel.PROP_CURRENT_MATCH)) {
                    sendModelMatchValue((Integer) evt.getNewValue());
                }
            }
        });

        final DtwData data = model.getData();
        setHasExamples(data.getNumTotalExamples() > 0);
        data.addPropertyChangeListener(new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(DtwData.PROP_NUMTOTALEXAMPLES)) {
                    if (data.getNumTotalExamples() == 0) {
                        setCanRun(false);
                        setHasExamples(false);
                    } else {
                        setCanRun(true);
                        setHasExamples(true);
                    }
                }
            }
        });
        data.addDataListener(new DtwData.DtwDataListener() {

            @Override
            public void exampleAdded(int whichClass) {
                updateStatusExampleAdded(whichClass);
            }

            @Override
            public void exampleDeleted(int whichClass) {
                updateStatusExampleDeleted(whichClass);
            }

            @Override
            public void allExamplesDeleted() {
                updateStatusAllExamplesDeleted();
            }

            @Override
            public void numExamplesChanged(int whichClass, int currentNumExamples) {
            }

        });
        w.getInputManager().addInputValueListener(new InputManager.InputListener() {
            @Override
            public void update(double[] vals) {
                updateInputs(vals);
            }

            @Override
            public void notifyInputError() {
            }
        });
    }

    public DtwModel getModel() {
        return model;
    }

    public void reAddLastExample() {
        model.getData().reAddLastExample();
    }

    public boolean hasExamples() {
        return (model.getData().getNumTotalExamples() > 0);
    }

    public void deleteLastExample() {
        //TODO: Have to update "last example" when we delete manually
        //Solution: have a linked list of n previous examples, n deleted examples...
        // XXX
        model.getData().deleteLastExample();       
    }

    public void updateModel(DtwModel m, DtwSettings newDtwSettings, String[] selectedInputNames) {
        m.setSettings(newDtwSettings);
        System.out.println("SEtting settings to: ");
        newDtwSettings.dumpToConsole();
        //XXX feature selection: do feature change here
    }

    public static enum RunningState {

        RUNNING, NOT_RUNNING
    };

    /**
     * Get the value of runningState
     *
     * @return the value of runningState
     */
    public RunningState getRunningState() {
        return runningState;
    }

    /**
     * Set the value of runningState
     *
     * @param runningState new value of runningState
     */
    private void setHasExamples(boolean hasExamples) {
        boolean oldHasExamples = this.hasExamples;
        this.hasExamples = hasExamples;
        propertyChangeSupport.firePropertyChange(PROP_HAS_EXAMPLES, oldHasExamples, hasExamples);
    }

    /**
     * Set the value of runningState
     *
     * @param runningState new value of runningState
     */
    private void setRunningState(RunningState runningState) {
        RunningState oldRunningState = this.runningState;
        this.runningState = runningState;
        propertyChangeSupport.firePropertyChange(PROP_RUNNING_STATE, oldRunningState, runningState);
    }

    public void startRunning() {
        if (runningState != RunningState.RUNNING) {
            model.startRunning();
            setRunningState(RunningState.RUNNING);
        }
    }

    public void stopRunning() {
        if (runningState == RunningState.RUNNING) {
            model.stopRunning();
            setRunningState(RunningState.NOT_RUNNING);
        }
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    /**
     * Remove PropertyChangeListener.
     *
     * @param listener
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    private void updateInputs(double[] vals) {
        if (model.getRecordingState() == DtwModel.RecordingState.RECORDING) {
            model.getData().addTrainingVector(vals);
        } else if (runningState == RunningState.RUNNING) {
            model.runOnVector(vals);
        }
    }

    //Need functions that GUI can call to start/stop running, update run mask, ...
    @Override
    public void addConnectionsListener(InputOutputConnectionsListener l) {
        //XXX
    }

    @Override
    public boolean[][] getConnectionMatrix() {
        ///XXX
        return new boolean[0][0];
    }

    @Override
    public boolean removeConnectionsListener(InputOutputConnectionsListener l) {
        return true;
        //XXX
    }

    @Override
    public void updateInputOutputConnections(boolean[][] newConnections) {
        //XXX
    }

    void deleteAllExamples() {
        /* for (DtwModel m : models) {
         m.deleteAllExamples();
         } */
        model.getData().deleteAll();
    }

    public DtwData getData() {
        return model.getData();
    }

    /**
     * Get the value of canRun
     *
     * @return the value of canRun
     */
    public boolean canRun() {
        return canRun;
    }

    /**
     * Set the value of canRun
     *
     * @param canRun new value of canRun
     */
    private void setCanRun(boolean canRun) {
        boolean oldCanRun = this.canRun;
        this.canRun = canRun;
        propertyChangeSupport.firePropertyChange(PROP_CANRUN, oldCanRun, canRun);
    }

    boolean isLegalTrainingValue(int whichOutput, float value) {
        if (whichOutput != 0) {
            throw new IllegalArgumentException("Invalid output " + whichOutput);
        }
        return (value < model.getNumGestures());
    }

    private void updateStatusExampleAdded(int whichClass) {
        w.getStatusUpdateCenter().update(this, "Example added for gesture " + whichClass);
    }

    private void updateStatusExampleDeleted(int whichClass) {
        w.getStatusUpdateCenter().update(this, "Example deleted for gesture " + whichClass);
    }

    private void updateStatusAllExamplesDeleted() {
        w.getStatusUpdateCenter().update(this, "All examples deleted");
    }

    private void sendModelUpdates(double[] currentDistances) {
        try {
            // w.getOutputManager().setNewComputedValues(currentDistances); //TODO XXX put back into output manager
            w.getOSCSender().sendOutputValuesMessage(w.getOutputManager().getOutputGroup().getOscMessage(), currentDistances);
        } catch (IOException ex) {
            Logger.getLogger(DtwLearningManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void sendModelMatchValue(int newMatch) {
        if (newMatch != -1) {
            try {
                w.getOSCSender().sendOutputMessage(model.getGestureName(newMatch));
            } catch (IOException ex) {
                Logger.getLogger(DtwLearningManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}
