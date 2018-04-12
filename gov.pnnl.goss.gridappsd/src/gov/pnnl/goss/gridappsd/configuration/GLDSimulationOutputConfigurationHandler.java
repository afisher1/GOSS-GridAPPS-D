/*******************************************************************************
 * Copyright  2017, Battelle Memorial Institute All rights reserved.
 * Battelle Memorial Institute (hereinafter Battelle) hereby grants permission to any person or entity 
 * lawfully obtaining a copy of this software and associated documentation files (hereinafter the 
 * Software) to redistribute and use the Software in source and binary forms, with or without modification. 
 * Such person or entity may use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of 
 * the Software, and may permit others to do so, subject to the following conditions:
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the 
 * following disclaimers.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and 
 * the following disclaimer in the documentation and/or other materials provided with the distribution.
 * Other than as used herein, neither the name Battelle Memorial Institute or Battelle may be used in any 
 * form whatsoever without the express written consent of Battelle.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY 
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL 
 * BATTELLE OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, 
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE 
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED 
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING 
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED 
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * General disclaimer for use with OSS licenses
 * 
 * This material was prepared as an account of work sponsored by an agency of the United States Government. 
 * Neither the United States Government nor the United States Department of Energy, nor Battelle, nor any 
 * of their employees, nor any jurisdiction or organization that has cooperated in the development of these 
 * materials, makes any warranty, express or implied, or assumes any legal liability or responsibility for 
 * the accuracy, completeness, or usefulness or any information, apparatus, product, software, or process 
 * disclosed, or represents that its use would not infringe privately owned rights.
 * 
 * Reference herein to any specific commercial product, process, or service by trade name, trademark, manufacturer, 
 * or otherwise does not necessarily constitute or imply its endorsement, recommendation, or favoring by the United 
 * States Government or any agency thereof, or Battelle Memorial Institute. The views and opinions of authors expressed 
 * herein do not necessarily state or reflect those of the United States Government or any agency thereof.
 * 
 * PACIFIC NORTHWEST NATIONAL LABORATORY operated by BATTELLE for the 
 * UNITED STATES DEPARTMENT OF ENERGY under Contract DE-AC05-76RL01830
 ******************************************************************************/ 
package gov.pnnl.goss.gridappsd.configuration;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.apache.felix.dm.annotation.api.Component;
import org.apache.felix.dm.annotation.api.ServiceDependency;
import org.apache.felix.dm.annotation.api.Start;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;

import gov.pnnl.goss.cim2glm.CIMImporter;
import gov.pnnl.goss.cim2glm.queryhandler.QueryHandler;
import gov.pnnl.goss.gridappsd.api.ConfigurationHandler;
import gov.pnnl.goss.gridappsd.api.ConfigurationManager;
import gov.pnnl.goss.gridappsd.api.LogManager;
import gov.pnnl.goss.gridappsd.api.PowergridModelDataManager;
import gov.pnnl.goss.gridappsd.data.handlers.BlazegraphQueryHandler;
import gov.pnnl.goss.gridappsd.utils.GridAppsDConstants;
import pnnl.goss.core.Client;


@Component
public class GLDSimulationOutputConfigurationHandler extends BaseConfigurationHandler implements ConfigurationHandler {//implements ConfigurationManager{

	private static Logger log = LoggerFactory.getLogger(GLDSimulationOutputConfigurationHandler.class);
	Client client = null; 
	
	@ServiceDependency
	private volatile ConfigurationManager configManager;
	@ServiceDependency
	private volatile PowergridModelDataManager powergridModelManager;
	@ServiceDependency
	volatile LogManager logManager;
	
	public static final String TYPENAME = "GridLAB-D Simulation Output";
	public static final String MODELID = "model_id";
	public static final String DICTIONARY_FILE = "dictionary_file";
	
	public GLDSimulationOutputConfigurationHandler() {
	}
	 
	public GLDSimulationOutputConfigurationHandler(ConfigurationManager configManager, 
			PowergridModelDataManager powergridModelManager, LogManager logManager) {
		this.configManager = configManager;
		this.powergridModelManager = powergridModelManager;
		this.logManager = logManager;
	}
	
	
	@Start
	public void start(){
		if(configManager!=null) {
			configManager.registerConfigurationHandler(TYPENAME, this);
		}
		else { 
			//TODO send log message and exception
			log.warn("No Config manager avilable for "+getClass());
		}
		
		if(powergridModelManager == null){
			//TODO send log message and exception
		}
	}

	@Override
	public void generateConfig(Properties parameters, PrintWriter out, String processId, String username) throws Exception {
		logRunning("Generating simulation output configuration file using parameters: "+parameters, processId, username, logManager);

		String modelId = GridAppsDConstants.getStringProperty(parameters, MODELID, null);
		if(modelId==null || modelId.trim().length()==0){
			logError("No "+MODELID+" parameter provided", processId, username, logManager);
			throw new Exception("Missing parameter "+MODELID);
		}
		
		//If passed in, use location of dictionary file, otherwise it will attempt to generate it
		File dictFile = null;
		String dictFilePath = GridAppsDConstants.getStringProperty(parameters, DICTIONARY_FILE, null);
		if(dictFilePath!=null){
			dictFile = new File(dictFilePath);
		}
		
		String bgHost = configManager.getConfigurationProperty(GridAppsDConstants.BLAZEGRAPH_HOST_PATH);
		if(bgHost==null || bgHost.trim().length()==0){
			bgHost = BlazegraphQueryHandler.DEFAULT_ENDPOINT; 
		}
		
		
		
		Reader measurementFileReader;
		
		if(dictFile!=null && dictFile.exists()){
			measurementFileReader = new FileReader(dictFile);
		} else {
			//TODO write a query handler that uses the built in powergrid model data manager that talks to blazegraph internally
			QueryHandler queryHandler = new BlazegraphQueryHandler(bgHost);
			queryHandler.addFeederSelection(modelId);
			CIMImporter cimImporter = new CIMImporter(); 
			StringWriter dictionaryStringOutput = new StringWriter();
			PrintWriter dictionaryOutput = new PrintWriter(dictionaryStringOutput);
			cimImporter.generateDictionaryFile(queryHandler, dictionaryOutput);
			String dictOut = dictionaryStringOutput.toString();
			measurementFileReader = new StringReader(dictOut);
		}
		
		String result = CreateGldPubs(measurementFileReader, processId, username);
		//return result;
		out.write(result);
		out.flush();
		
		logRunning("Finished generating simulation output configuration file.", processId, username, logManager);

	}
	
	
	
	
	String CreateGldPubs(Reader measurementFileReader, String processId, String username) throws FileNotFoundException {
		String jsonObjStr = "";
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		JsonObject gldConfigObj = new JsonObject();
//		JsonObject gldPublications = new JsonObject();
		
		try {
			JsonObject jsonObj = gson.fromJson(measurementFileReader, JsonObject.class);
			JsonArray feeders = (JsonArray) jsonObj.get("feeders");
			Iterator<JsonElement> iter = feeders.iterator();
			while(iter.hasNext()) {
				JsonObject feederInfo = (JsonObject) iter.next();
				JsonArray feederMeasurements = (JsonArray) feederInfo.get("measurements");
				Iterator<JsonElement> feederMeasurementsIter = feederMeasurements.iterator();
				Map<String, JsonArray> measurements = new HashMap<String, JsonArray>();
				while(feederMeasurementsIter.hasNext()) {
					JsonObject feederMeasurement = (JsonObject) feederMeasurementsIter.next();
					parseMeasurement(measurements, feederMeasurement);
				}
				for(Map.Entry<String, JsonArray> entry : measurements.entrySet()) {
					gldConfigObj.add(entry.getKey(), entry.getValue());
				}
				measurements.clear();
			}
//			gldConfigObj.add("publications", gldPublications);
			jsonObjStr = gson.toJson(gldConfigObj);
			
		} catch (JsonIOException e) {
			logError("Error while generating simulation output: "+e.getMessage(), processId, username, logManager);
			throw e;
		} catch (JsonParseException e) {
			logError("Error while generating simulation output: "+e.getMessage(), processId, username, logManager);
			throw e;
		}
		return jsonObjStr;
	}
	
	void parseMeasurement(Map<String, JsonArray> measurements, JsonObject measurement) throws JsonParseException{
		String objectName;
		String propertyName;
		String measurementType;
		String phases;
		String conductingEquipmentType;
		String conductingEquipmentName;
		String connectivityNode;
		if(!measurement.has("measurementType") || !measurement.has("phases") || !measurement.has("ConductingEquipment_type") || !measurement.has("ConductingEquipment_name") || !measurement.has("ConnectivityNode")) {
			throw new JsonParseException("CimMeasurementsToGldPubs::parseMeasurement: The JsonObject measurements must have the following keys: measurementType, phases, ConductingEquipment_type,ConductingEquipment_name, and ConnectivityNode.");
		}
		measurementType = measurement.get("measurementType").getAsString();
		phases = measurement.get("phases").getAsString();
		conductingEquipmentType = measurement.get("name").getAsString();
		conductingEquipmentName = measurement.get("ConductingEquipment_name").getAsString();
		connectivityNode = measurement.get("ConnectivityNode").getAsString();
		if(conductingEquipmentType.contains("LinearShuntCompensator")) {
			if(measurementType.equals("VA")) {
				objectName = "cap_"+conductingEquipmentName;
				propertyName = "shunt_" + phases;
			} else if (measurementType.equals("Pos")) {
				objectName = "cap_"+conductingEquipmentName;
				propertyName = "switch" + phases;
			} else if (measurementType.equals("PNV")) {
				objectName = "cap_"+conductingEquipmentName;
				propertyName = "voltage_" + phases;
			} else {
				throw new JsonParseException(String.format("CimMeasurementsToGldPubs::parseMeasurement: The value of measurementType is not a valid type.\nValid types for LinearShuntCompensators are VA, Pos, and PNV.\nmeasurementType = %s.",measurementType));
			}
		} else if (conductingEquipmentType.contains("PowerTransformer")) {
			if(measurementType.equals("VA")) {
				objectName = "tx_"+conductingEquipmentName;
				propertyName = "power_out_" + phases;
			} else if (measurementType.equals("Pos")) {
				objectName = "tx_"+conductingEquipmentName;
				propertyName = "tap_" + phases;
			} else {
				throw new JsonParseException(String.format("CimMeasurementsToGldPubs::parseMeasurement: The value of measurementType is not a valid type.\nValid types for PowerTransformers are VA and Pos.\nmeasurementType = %s.",measurementType));
			}
		} else if (conductingEquipmentType.contains("ACLineSegment")) {
			if(measurementType.equals("VA")) {
				objectName = "line_"+conductingEquipmentName;
				propertyName = "power_out_" + phases;
			} else if (measurementType.equals("PNV")) {
				objectName = connectivityNode;
				propertyName = "voltage_" + phases;
			} else {
				//TODO this is temporary until the output generation is fixed
				objectName = "";
				propertyName = "";
//				throw new JsonParseException(String.format("CimMeasurementsToGldPubs::parseMeasurement: The value of measurementType is not a valid type.\nValid types for ACLineSegments are VA and PNV.\nmeasurementType = %s.",measurementType));
			}
		} else {
			//TODO this is temporary until the output generation is fixed
			objectName = "";
			propertyName = "";
//			throw new JsonParseException(String.format("CimMeasurementsToGldPubs::parseMeasurement: The value of ConductingEquipment_type is not a recognized object type.\nValid types are ACLineSegment, LinearShuntCompesator, and PowerTransformer.\nConductingEquipment_type = %s.",conductingEquipmentType));
		}
		if(measurements.containsKey(objectName)) {
			measurements.get(objectName).add(new JsonPrimitive(propertyName));
		} else {
			JsonArray newMeasurements = new JsonArray();
			newMeasurements.add(new JsonPrimitive(propertyName));
			measurements.put(objectName, newMeasurements);
		}
	}
}