/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package wekimini.studyanalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 *
 * @author louismccallum
 */
public class LogsExperiment {
    
    public final String STUDY_DIR = "featurnator_study_1";
    public final String PROJECT_NAME = "featurnator_study_1_notest.txt";
    public final String ROOT_DIR = "/Users/louismccallum/Documents/Goldsmiths/Study1_logs";
    public final String RESULTS_DIR = "/Users/louismccallum/Documents/Goldsmiths/Study1_analysis";
    public Participant participant;
    public Iterator participantIterator;
    public final String[] blackList = new String[] {"Esben_Pilot", "Francisco_Pilot", "Sam_Pilot", "1", "P1", "P10"};
    public ArrayList<Participant> participants = new ArrayList();
    
    public static void main(String[] args)
    {
        LogsExperiment e = new LogsExperiment();
        e.runTests();
    }
    
    public String getRootDir()
    {
        return ROOT_DIR;
    }
    
    public String getProjectName()
    {
        return PROJECT_NAME;
    }
    
    public String getResultsDir()
    {
        return RESULTS_DIR;
    }
    
    public String[] getBlacklist()
    {
        return blackList;
    }
    
    
    public void runTests()
    {
        HashMap<String, String> projects = getProjectLocations();
        participantIterator = projects.entrySet().iterator();
        if(participantIterator.hasNext())
        {
            runForNextParticipant();
        }
    }
    
    public void reset()
    {
        participant = new Participant();
    }
    
    public boolean isBlackListed(String pID)
    {
        for(String blackListed : getBlacklist())
        {
            if(pID.equals(blackListed))
            {
                return true;
            }
        }
        return false;
    }
    
    public void logParticipant()
    {
       participants.add(participant);
    }
    
    public void logAll()
    {
        System.out.println("LOGGING ALL");
        ObjectMapper json = new ObjectMapper();
        DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        Date date = new Date();
        String path = getResultsDir() + File.separator + "logExperiment" + dateFormat.format(date) + ".json";
        try{
            json.writeValue(new FileOutputStream(path), participants);
        }
        catch(Exception e)
        {
            System.out.println("ERROR: writing file");
        }
        System.exit(0);
    }
    
    public void runForNextParticipant()
    {
        reset();
                
        if(participantIterator.hasNext())
        {
            Map.Entry pair = (Map.Entry)participantIterator.next();
       
            while(isBlackListed((String)pair.getKey()))
            {
                System.out.println("Skipping " + (String)pair.getKey() + "(blacklisted)");
                if(!participantIterator.hasNext())
                {
                    logAll();
                    return;
                }
                pair = (Map.Entry)participantIterator.next();
            }

            System.out.println(pair.getKey() + " = " + pair.getValue());
            String location = (String) pair.getValue();
            String pID = (String) pair.getKey();
            ArrayList<String> lines = new ArrayList();
            participant.participantID = pID;
            try 
            {
                Files.lines(Paths.get(location)).forEach(line->lines.add(line));
            } 
            catch (Exception e)
            {
                
            }
            long cumSumRunning = 0;
            long cumSumRecording = 0;
            long prevStart = 0;
            int cvCtr = 0;
            int runCtr = 0;
            int newFeatureRunCtr = 0;
            int newDataRunCtr = 0;
            boolean running = false;
            boolean recording = false;
            boolean didChangeFeatures = false;
            boolean didChangeData = false;
            ArrayList<String> exploredFeatures = new ArrayList();
            
            for(String line : lines)
            {
                String split[] = line.split(",");
                if(split[2].equals("START_RUN"))
                {
                    prevStart = Long.parseUnsignedLong(split[0]);
                    running = true;
                }
                if(running && split[2].equals("RUN_STOP"))
                {
                    long endTime = Long.parseUnsignedLong(split[0]);
                    cumSumRunning += (endTime - prevStart);
                    runCtr++;
                    running = false;
                    newFeatureRunCtr += didChangeFeatures ? 1 : 0;
                    newDataRunCtr += didChangeData ? 1 : 0;
                    didChangeFeatures = false;
                    didChangeData = false;
                }
                if(split[2].equals("SUPERVISED_RECORD_START"))
                {
                    prevStart = Long.parseUnsignedLong(split[0]);
                    recording = true;
                    didChangeData = true;
                }
                if(recording && split[2].equals("SUPERVISED_RECORD_STOP"))
                {
                    long endTime = Long.parseUnsignedLong(split[0]);
                    cumSumRecording += (endTime - prevStart);
                    recording = false;
                }
                if(line.contains("CROSS_VALIDATATION"))
                {
                    cvCtr++;
                }
                if(split[2].equals("FEATURE_REMOVED") || split[2].equals("FEATURE_ADDED"))
                {
                    didChangeFeatures = true;
                    if(split[2].equals("FEATURE_ADDED"))
                    {
                        if(split[3].length() > 3)
                        {
                            for(int i = 3; i < split.length; i++)
                            {
                                String ft = split[i];
                                if(i == 3)
                                {
                                    ft = ft.substring(1);
                                }
                                if(i == split.length - 1)
                                {
                                    ft = ft.substring(0, ft.length() - 1);
                                }
                                if(!exploredFeatures.contains(ft))
                                {
                                    exploredFeatures.add(ft);
                                }
                            } 
                        } 
                    }
                }
            }
            System.out.println(cumSumRunning);
            System.out.println(cumSumRecording);
            System.out.println(cvCtr);
            String[] f = new String[exploredFeatures.size()];
            f = exploredFeatures.toArray(f);
            participant.features.put("explored", f);
            participant.timeSpentRunning = cumSumRunning;
            participant.timeSpentRecording = cumSumRecording;
            participant.cvCount = cvCtr;
            participant.runCount = runCtr;
            participant.newDataRunCount = newDataRunCtr;
            participant.newFeatureRunCount = newFeatureRunCtr;
            participantIterator.remove(); 
            logParticipant();
            runForNextParticipant();
        }
        else
        {
            logAll();
            return;
        }
    }
     
    public HashMap<String, String> getProjectLocations()
    {
        HashMap<String, String> projects = new HashMap();
        File folder = new File(getRootDir());
        System.out.println(getRootDir());
        File[] listOfFiles = folder.listFiles();
        for(File file : listOfFiles)
        {
            if(file.isDirectory())
            {
                String pID = file.getName();
                File studyFolder = new File(file.getAbsolutePath() + File.separator + STUDY_DIR);
                File[] listOfStudyFiles = studyFolder.listFiles();
                for(File studyFile : listOfStudyFiles)
                {
                    if(studyFile.getName().contains(getProjectName()))
                    {
                        String projectFile = studyFile.getAbsolutePath();
                        projects.put(pID, projectFile);
                        break;
                    } 
                }
            }
        }
        return projects;
    }
}