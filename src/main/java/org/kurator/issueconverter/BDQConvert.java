/** BDQConvert.java
 *
 * Copyright 2018 President and Fellows of Harvard College
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 * 
 */
package org.kurator.issueconverter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashSet;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.QuoteMode;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * BDQConvert special purpose class to extract data quality framework information from by convention tabular organization within
 * TDWG BDQ (Biodiversity Data Quality TG) github issues to csv suitable for examination in spreadsheets or conversion to RDF.
 *
 * @author Paul J. Morris
 *
 */
public class BDQConvert {

	private static String version = "0.0.1-SNAPSHOT";

	private static String commandLine = "java -jar issueconverter-"+version+"-jar-with-dependencies.jar";

	private static final Log logger = LogFactory.getLog(BDQConvert.class);
	
	private static Map<String,String> criterionMap;
	private static Map<String,String> enhancementMap;
	
	/**
	 * If true, also generate a csv file of measures on MultiRecord for each SingleRecord validation.
	 */
	private static boolean regenerateMultiRecord = true;
	
	private static Map<String,String> measureGuids;
	private static List<String> measuresAllowingIPNM;  // list of measures that allow Internal Prerequsites Not Met to be complete.
	
	
	/**
	 * main method, expected entry point, launched from command line.
	 * @param args
	 */
	public static void main(String[] args) {

		Options options = new Options();
		options.addOption("f", true, "JSON file to convert");		
		options.addOption("u", true, "UseCase-Test csv file");	
		options.addOption("l", true, "Label Mapping csv file");
		options.addOption("g", true, "Additional Guid Mapping csv file");
		options.addOption("a", true, "Argument Mapping csv file");
		options.addOption("nm", false, "Don't Regenerate MultiRecordMeasures csv file");

		// maping of last word of term-actions onto criterion
		criterionMap = new HashMap<String,String>();
		criterionMap.put("COMPLETE", "Complete");
		criterionMap.put("CONSISTENT", "Consistent");
		criterionMap.put("FOUND", "Found");
		criterionMap.put("INRANGE", "InRange");
		criterionMap.put("LIKELY", "Likely");
		criterionMap.put("NOTZERO", "Likely");
		criterionMap.put("NOTEMPTY", "NotEmpty");
		criterionMap.put("STANDARD", "Standard");
		criterionMap.put("INTEGER", "Standard");
		criterionMap.put("UNAMBIGUOUS", "Unambiguous");
		
		// mapping of last word of term-actions onto enhancement
		enhancementMap = new HashMap<String,String>();
		enhancementMap.put("ASSUMEDDEFAULT", "AssumedDefault");
		enhancementMap.put("CONVERTED", "Converted");
		enhancementMap.put("STANDARDIZED", "Standardized");
		enhancementMap.put("FROM", "FillInFrom");
		enhancementMap.put("TRANSPOSED", "Transposed");
		

		CommandLineParser parser = new DefaultParser();
		try {
			CommandLine cmd = parser.parse( options, args);
			String useCaseFilename = null;
			if (cmd.hasOption("u")) { 
				useCaseFilename = cmd.getOptionValue("u");
			}
			String labelMappingFilename = null;
			if (cmd.hasOption("l")) { 
				labelMappingFilename = cmd.getOptionValue("l");
			}
			String guidMappingFilename = null;
			if (cmd.hasOption("g")) { 
				guidMappingFilename = cmd.getOptionValue("g");
			}
			String argumentMappingFilename = null;
			if (cmd.hasOption("a")) { 
				argumentMappingFilename = cmd.getOptionValue("a");
			}			
			if (cmd.hasOption("nm")) { 
				regenerateMultiRecord = false;
			}
			if (cmd.hasOption("f")) { 
				convert(cmd.getOptionValue("f"), useCaseFilename, labelMappingFilename, guidMappingFilename, argumentMappingFilename);
			} else {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp( commandLine, options );
			}
		} catch (ParseException e) {
			System.out.println("Error parsing command line: " + e.getMessage());
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp( commandLine, options );
		}		

	}

	protected static void convert(String filename, String useCaseFilename, String labelMappingFilename, String guidMappingFilename, String argumentMappingFilename) { 
		logger.debug(filename);
		File file = new File(filename);
		logger.debug(file.canRead());
		logger.debug(useCaseFilename);
		boolean includeUseCases = false;
		boolean includeArgumentGuids = false;
		boolean includeAdditionalGuids = false;
		MultiValuedMap<String, String> useCaseMap = new HashSetValuedHashMap<>();
		if (useCaseFilename != null && useCaseFilename.length()>0) {
			File useCaseFile = new File(useCaseFilename);
			logger.debug(useCaseFile.canRead());
			if (useCaseFile.canRead()) { 
				try { 
					FileReader reader = new FileReader(useCaseFile);
					CSVParser csvParser = new CSVParser(reader,CSVFormat.DEFAULT.withFirstRecordAsHeader());
					List<CSVRecord> useCaseList = csvParser.getRecords();
					Iterator<CSVRecord> i = useCaseList.iterator();
					while (i.hasNext()) { 
						CSVRecord useCaseRecord = i.next();
						String useCase = useCaseRecord.get("UseCase").trim();
						String includedTests = useCaseRecord.get("LabelsOfTestsIncluded");
						String[] bits = includedTests.split("[|]");
						for (String bit: bits) {
							String testLabel = bit.trim();
							if (testLabel.length()>0) {
								useCaseMap.put(testLabel, useCase);
							}
						}
					}
					includeUseCases = true;
				} catch (IOException e) { 
					logger.error(e.getMessage());
				}
			}
		}
		
		Map<String,String> specificationMap = new HashMap<String,String>();
		Map<String,String> methodMap = new HashMap<String,String>();
		Map<String,String> policyMap = new HashMap<String,String>();
		if (guidMappingFilename != null && guidMappingFilename.length()>0) {
			File guidMappingFile = new File(guidMappingFilename);
			logger.debug(guidMappingFile.canRead());
			if (guidMappingFile.canRead()) { 
				try { 
					FileReader reader = new FileReader(guidMappingFile);
					CSVParser csvParser = new CSVParser(reader,CSVFormat.DEFAULT.withFirstRecordAsHeader());
					List<CSVRecord> argumentList = csvParser.getRecords();
					Iterator<CSVRecord> i = argumentList.iterator();
					while (i.hasNext()) { 
						CSVRecord argumentRecord = i.next();
						String guid = argumentRecord.get("GUID").trim();
						String method = argumentRecord.get("Method");
						String spec = argumentRecord.get("Specification");
						String policy = argumentRecord.get("Policy");
						specificationMap.put(guid, spec);
						methodMap.put(guid, method);
						policyMap.put(guid, policy);
					}
					includeAdditionalGuids = true;
				} catch (IOException e) { 
					logger.error(e.getMessage());
				}
			}
		}
		
		Map<String,String> argumentMap = new HashMap<String,String>();
		if (argumentMappingFilename != null && argumentMappingFilename.length()>0) {
			File argumentMappingFile = new File(argumentMappingFilename);
			logger.debug(argumentMappingFile.canRead());
			if (argumentMappingFile.canRead()) { 
				try { 
					FileReader reader = new FileReader(argumentMappingFile);
					CSVParser csvParser = new CSVParser(reader,CSVFormat.DEFAULT.withFirstRecordAsHeader());
					List<CSVRecord> argumentList = csvParser.getRecords();
					Iterator<CSVRecord> i = argumentList.iterator();
					while (i.hasNext()) { 
						CSVRecord argumentRecord = i.next();
						String guid = argumentRecord.get("Argument").trim();
						String label = argumentRecord.get("Label");
						String spec = argumentRecord.get("Specification");
						logger.debug(label+spec);
						argumentMap.put(label + spec, guid); // concatenated label for argument and specification guid are the key, value is the argument guid
					}
					includeArgumentGuids = true;
				} catch (IOException e) { 
					logger.error(e.getMessage());
				}
			}
		}
		
		Map<String,String> prefLabelMap = new HashMap<String,String>();
		if (labelMappingFilename != null && labelMappingFilename.length()>0) {
			File labelMapFile = new File(labelMappingFilename);
			logger.debug(labelMapFile.canRead());
			if (labelMapFile.canRead()) { 
				try { 
					FileReader reader = new FileReader(labelMapFile);
					CSVParser csvParser = new CSVParser(reader,CSVFormat.DEFAULT.withFirstRecordAsHeader());
					List<CSVRecord> labelList = csvParser.getRecords();
					Iterator<CSVRecord> i = labelList.iterator();
					while (i.hasNext()) { 
						CSVRecord useCaseRecord = i.next();
						String guid = useCaseRecord.get("GUID").trim();
						String prefLabel = useCaseRecord.get("Preferred Label");
						prefLabelMap.put(guid, prefLabel);
					}
					includeUseCases = true;
				} catch (IOException e) { 
					logger.error(e.getMessage());
				}
			}
		}
		if (file.canRead()) { 

			/**
			#,GUID,IDs,Variable,Description (test - FAIL),Description (test - PASS),Specification (Technical Description)
			,Record Resolution,Term Resolution,Data Dependency,Output Type,Example,Darwin Core Class,Darwin Core Terms,
			DQ Dimension,Severity,Warning Type,Source,References,Specification Last Updated, Example Implementations (Mechanisms),
			Link to Specification Source Code,Comments and Questions,Notes ,			
			 **/	
			// Add use cases
			// Add skos:prefLabel

			// Headers as they are to be produced in the output csv.
			ArrayList<String> outputHeaders = new ArrayList<String>();
			outputHeaders.add("Label");  // Variable, human readable identifier for test --> rdfs:label
			outputHeaders.add("issueNumber");   // the issue number (unused downstream)
			outputHeaders.add("historyNoteUrl") ;  //  -> to skos:historyNote
			outputHeaders.add("iri");   // versioned IRI with date 
			outputHeaders.add("term_iri");   // IRI for term without version  --> 
			outputHeaders.add("issued");   // date issued
			//  outputHeaders.add("Confirmed");  // Is there a confirmed label, now removed from all records.
			outputHeaders.add("term_localName");  // GUID, machine readable identifier for test
			outputHeaders.add("DateLastUpdated");  // Most recent modification date for test
			outputHeaders.add("prefLabel");  // skos preferred label (skos:prefLabel) for test
			// outputHeaders.add("IE Category");  // broad concepts the information elements fall into ** Deprecated **
			outputHeaders.add("IE Class");   // Darwin Core class(es) the information elements fall into
			// outputHeaders.add("Information Element");   // Framework concept, the list of Darwin Core terms forming specific information elements
			outputHeaders.add("InformationElement:ActedUpon");   // Framework concept, the list of Darwin Core terms forming specific information elements
			outputHeaders.add("InformationElement:Consulted");   // Framework concept, the list of Darwin Core terms forming specific information elements
			outputHeaders.add("Parameters");   // Parameters for tests.  
			outputHeaders.add("ExpectedResponse");   // Specification expected response Framework property	
			outputHeaders.add("SpecificationGuid"); // uuid for the Specification
			outputHeaders.add("MethodGuid"); // uuid for the Method
			outputHeaders.add("AuthoritiesDefaults");   // Specification authorities and default values Framework property	
			outputHeaders.add("Description"); // Human readable summary of structured concepts in the test --> rdfs:comment on DataQualityNeed
			// TODO: Unused, replace
			outputHeaders.add("Criterion Label"); // Human readable summary of structured concepts in the test 
			outputHeaders.add("Type");  // Output Type  Framework Class: Validation/Amendment/Measure/Issue  -->rdf:type
			outputHeaders.add("Resource Type");   // Resource Type Single- or Multi- Record  Framework concept bdqffdq;hasResourcetype
			outputHeaders.add("Dimension");	 //DQ Dimension  Framework concept  --> bdqffdq:hasDataQualityDimension
			outputHeaders.add("Criterion");  // DQ Criterion for validations  --> bdqffdq:hasCriterion
			outputHeaders.add("Enhancement");  // DQ Enhancement for amendments  --> bdqffdq:hasEnhancement
			// outputHeaders.add("Warning Type");  // Warning Type
			outputHeaders.add("Examples");  // Two examples --> skos;example 
			outputHeaders.add("Source");  // Source from which the test was originally drawn
			// outputHeaders.add("Test Prerequisites");  // No longer present, merged into specification
			outputHeaders.add("References");  // References -> dcterms:bibliographicCitation
			outputHeaders.add("Example Implementations (Mechanisms)");  // Mechanisms 
			outputHeaders.add("Link to Specification Source Code"); //Link to Specification Source Code
			outputHeaders.add("Notes");   // Notes -> skos:historyNote
			outputHeaders.add("IssueState");  // open or closed  --> unused
			outputHeaders.add("IssueLabels");  // Labels present on the github issue. --> unused
			outputHeaders.add("UseCases"); // UseCases the test is included in 
			outputHeaders.add("ArgumentGuids"); // List of guids for arguments parsed from authoritiesDefaults	

			// Headers as they appear as keys in the key/value markdown table in the issues
			ArrayList<String> headers = new ArrayList<String>();
			headers.add("GUID");  // GUID --> term_localName
			headers.add("Label");  // Variable
			// headers.add("Output Type");  // Output Type   Class: Validation/Amendment/Measure  ** Changing to TestType **
			headers.add("TestType");  // Output Type   Class: Validation/Amendment/Measure
			headers.add("Resource Type");   // Resource Type
			headers.add("Darwin Core Class");  // Darwin Core Class
			// headers.add("Information Elements");  // replaced with ActedUpon/Consulted
			headers.add("Information Elements ActedUpon");    
			headers.add("Information Elements Consulted");    
			headers.add("Expected Response");  // specification expected response, replaces pass/fail descriptiosn and prerequisites.
			// headers.add("Dimension");  // Information Element Category   ** To be removed ** 
			headers.add("Data Quality Dimension");	 //DQ Dimension
			headers.add("Term-Actions");  
			headers.add("Description");  
			headers.add("Warning Type");  // Warning Type
			headers.add("Examples");  
			headers.add("Source");  // Source
			headers.add("References");  // References
			headers.add("Specification Last Updated");  // overrides updated_at
			headers.add("Example Implementations (Mechanisms)");  
			headers.add("Link to Specification Source Code"); //Link to Specification Source Code
			headers.add("Notes");   // Notes
			headers.add("Description");
			headers.add("Parameter(s)"); // Parameters for the test in the form bdq:sourceAuthority default="defaultvalue"
			headers.add("Source Authority"); // to append to the expected response.
			//headers.add("Fail Description");   
			//headers.add("Pass Description");
			//headers.add("Test Prerequisites");  

			InputStream is;
			OutputStream os;
			OutputStream osMeasures;
			try {
				String measureFilename = "multirecord_measures.csv";
				os = new FileOutputStream("output.csv");
				osMeasures = new FileOutputStream(measureFilename);

				CSVPrinter outputPrinter = new CSVPrinter(new FileWriter("output.csv", true), CSVFormat.DEFAULT.withQuoteMode(QuoteMode.ALL));
				outputPrinter.printRecord(outputHeaders);
				
				CSVPrinter outputPrinterMeasures = null;
				if (regenerateMultiRecord) { 
					measuresAllowingIPNM = new ArrayList<String>();
					outputPrinterMeasures = new CSVPrinter(new FileWriter(measureFilename, true), CSVFormat.DEFAULT.withQuoteMode(QuoteMode.ALL));
					outputPrinterMeasures.printRecord(outputHeaders);
					
					ClassLoader classloader = Thread.currentThread().getContextClassLoader();
					InputStream mmguid_inputStream = classloader.getResourceAsStream("multirecord_measure_guids.csv");
					CSVParser guidReader = new CSVParser(new InputStreamReader(mmguid_inputStream,"UTF-8"),CSVFormat.DEFAULT.withFirstRecordAsHeader());
					measureGuids = new HashMap<String,String>();
					for (CSVRecord csvRecord : guidReader) {
						String guid = csvRecord.get("GUID");
						String label = csvRecord.get("Label");
						//logger.debug(label + ":" + guid);
						measureGuids.put(label, guid);
						try { 
							String allowIPNM = csvRecord.get("AllowIPNM");
							if (allowIPNM.equals("Yes")) { 
								measuresAllowingIPNM.add(label);
							}
						} catch (Exception e) { 
							logger.debug(e.getMessage());
						}
					}
				}
				
				is = new FileInputStream(file);
				String jsonTxt = IOUtils.toString(is, "UTF-8");

				JSONArray jsonArray = new JSONArray(jsonTxt);
				System.out.println(jsonArray.length());

				HashMap<String,String> allkeys = new HashMap<String,String>();

				for (int x=0; x<jsonArray.length(); x++) { 
					JSONObject element = jsonArray.getJSONObject(x);
					String body = (String) element.get("body");	        	
					Integer number = (Integer) element.get("number");
					String historyNoteUrl = element.getString("url");
					String state = (String) element.get("state");
					String updated_at = (String) element.get("updated_at");
					if (updated_at.length()>10) { 
						// per TDWG standards description specification, use yyyy-mm-dd as the version, leave off time.
						updated_at = updated_at.substring(0, 10);
					}
					System.out.println("// #" + Integer.toString(number));	            

					StringBuilder issueLabels = new StringBuilder();
					JSONArray labels = element.getJSONArray("labels");
					String separator = "";
					for (int y=0; y<labels.length(); y++) {
						JSONObject l = labels.getJSONObject(y);
						String label = l.getString("name");
						issueLabels.append(separator).append(label);
						separator = " ";
					}
					if (issueLabels.toString().contains("DO NOT IMPLEMENT")) { 
						// skip this test, do not add to output spreadsheet.
					} else if (issueLabels.toString().contains("Template")) { 
						// template issues for generated measures, skip
					} else { 
						// generate output

						HashMap<String,String> csvLine = new HashMap<String,String>(); 
						HashMap<String,String> outputLine = new HashMap<String,String>();
						outputLine.put("issueNumber", Integer.toString(number));
						outputLine.put("historyNoteUrl", historyNoteUrl);
						outputLine.put("issued", updated_at);
						outputLine.put("IssueState", state);
						outputLine.put("IssueLabels", issueLabels.toString());
						if (issueLabels.toString().contains("CONFIRMED")) { 
							outputLine.put("Confirmed","CONFIRMED");
						} else { 
							outputLine.put("Confirmed","");
						}

						ArrayList<String> lines = new ArrayList<String>(Arrays.asList(body.split("\n")));
						Iterator<String> i = lines.iterator();
						String lastHeader = new String();
						ArrayList<String> valueAccumulator = new ArrayList<String>();
						String lvalue = "";
						while (i.hasNext()) { 
							String line = i.next().trim();
							line = line.replaceFirst("\\|", "").trim();
							ArrayList<String> cells = new ArrayList<String>(Arrays.asList(line.split("\\|")));
							Iterator<String> i2 = cells.iterator();
							String header = i2.next().replaceAll("\\*", "").trim();
							if (!header.equals("-----") && !header.equals("TestField")) { 
								if (header.trim().length()==0) { 
									header = lastHeader; 
									if (i2.hasNext()) { 
										String v = i2.next().trim();
										valueAccumulator.add(v);
										lvalue = String.join(",",valueAccumulator);
									}
								} else {  
									valueAccumulator.clear();
									lvalue = "";
									if (i2.hasNext()) { 
										lvalue = i2.next().trim();
									}
									valueAccumulator.add(lvalue);
								}
								csvLine.put(header, lvalue);
							}
							lastHeader = header;
						}
						String testLabel = new String();
						if (csvLine.get("Label")!=null && csvLine.get("Label").trim().length()>0) { 
							// Line must have a value in Label to be included in output.
							if (!csvLine.containsKey("Resource Type")) { csvLine.put("Resource Type","SingleRecord"); }  // see note below about single record.
							Set<String>keys = csvLine.keySet();
							Iterator<String> ikDateCheck = keys.iterator();
							while (ikDateCheck.hasNext()) { 
								// Specification Last Updated in markdown table overwrites updated_at value
								// for issue to allow non-normative updates to be distinguished from updates
								// that will require examination of implementations for compliance.
								String key = ikDateCheck.next();
								if (key.equals("Specification Last Updated")) { 
									String value = csvLine.get(key);
									if (value!=null && value.length()==10) { 
										updated_at = value;
									} 
								}
							}
							Iterator<String> ik = keys.iterator();
							String frameworkClass = "";
							// String dimension = "";
							StringBuilder terms = new StringBuilder();
							StringBuilder examples = new StringBuilder();
							String dwcClass = "";
							//String problemDescription = null;
							//String validationDescription = null;
							String specificationDescription = null;
							String description = null;
							String criterionLabel = null;
							String termActions = null;
							String resourceType = "SingleRecord";  /// assume this default value, see note below.
							String dqDimension = null;
							String sourceAuthority = null;
							List<String> parameters = new ArrayList<String>();

							while (ik.hasNext()) { 
								String key = ik.next();
								allkeys.put(key,key);
								String value = csvLine.get(key);

								if (key.equals("GUID")) { 
									outputLine.put("term_localName", value); 
									outputLine.put("term_iri","https://rs.tdwg.org/bdqcore/terms/" + value);
									outputLine.put("iri","https://rs.tdwg.org/bdqcore/terms/version/" + value + "-" + updated_at);
									String prefLabel = "Missing prefLabel";
									if (prefLabelMap.containsKey(value)) { 
										prefLabel = prefLabelMap.get(value);
									}
									outputLine.put("prefLabel",prefLabel);
									if (includeAdditionalGuids) { 
										if (specificationMap.containsKey(value)) { 
											outputLine.put("SpecificationGuid", specificationMap.get(value));
										}
										if (methodMap.containsKey(value)) { 
											outputLine.put("MethodGuid", methodMap.get(value));
										}
									}
								}
								if (key.equals("Label")) { 
									outputLine.put("Label", value); 
									testLabel = value;
								}
								if (key.equals("Specification Last Updated")) { 
									outputLine.put("DateLastUpdated", value); 
								}								
								//if (key.equals("Output Type")) { 
								//	outputLine.put("Type", value);
								//	frameworkClass = value;
								//}
								if (key.equals("TestType")) { 
									outputLine.put("Type", value);
									frameworkClass = value;
								}
								if (key.equals("Resource Type")) { 
									if (value==null || value.trim().equals("")) { 
										// Lee stripped all of the Resource Types out of the tests, as all appeared to be SingleRecord instead of MultiRecord
										// therefore assumme if value is absent that this is a SingleRecord test.
										value = "SingleRecord";
									}
									outputLine.put("Resource Type", value); 
									resourceType = value; 
								}
								if (key.equals("Data Quality Dimension")) { outputLine.put("Dimension", value); dqDimension=value; }
								if (key.equals("Warning Type")) { outputLine.put("Warning Type", value); }
								if (key.equals("Term-Actions")) { 
									termActions = value; 
								}
								if (key.equals("Source")) { outputLine.put("Source", value); }
								if (key.equals("References")) { outputLine.put("References", value); }
								if (key.equals("Parameter(s)")) { parameters.add(value); }
								if (key.equals("Examples")) { 
									if (!value.trim().equals("")) {
										examples.append(value);
										outputLine.put("Examples", examples.toString());
									}
								}
								if (key.equals("Example Implementations (Mechanisms)")) { outputLine.put("Example Implementations (Mechanisms)", value); }
								if (key.equals("Link to Specification Source Code")) { outputLine.put("Link to Specification Source Code", value); }
								if (key.equals("Notes")) { outputLine.put("Notes", value); }
								if (key.equals("Test Prerequisites")) { outputLine.put("Test Prerequisites", value); }
								//if (key.equals("Dimension")) {
								//	dimension = value;
								//	outputLine.put("IE Category", value); 
								//}
								//if (key.equals("Dimension")) {
								//	dimension = value;
								//	outputLine.put("IE Class", value); 
								//}
								//if (key.equals("Information Elements")) {
								//    outputLine.put("Information Element", value);
								//	terms.append(value).append(" ");
								//}
								if (key.equals("Information Elements ActedUpon")) {
									if (!value.trim().equals("")) {
										outputLine.put("InformationElement:ActedUpon", value);
										terms.append(value).append("@ActedUpon ");
									}
								}
								if (key.equals("Information Elements Consulted")) {
									if (!value.trim().equals("")) {
										outputLine.put("InformationElement:Consulted", value);
										terms.append(value).append("@Consulted ");
									}
								}
								if (key.equals("Darwin Core Class")) { 
									dwcClass = value;
									outputLine.put("IE Class", value); 
								}	        		
								if (key.equals("Expected Response")) {  specificationDescription = value; }
								if (key.equals("Source Authority")) {  sourceAuthority = value; }
								//if (key.equals("Fail Description")) {  problemDescription = value; }
								if (key.equals("Description")) {  description = value; }
								//if (key.equals("Pass Description")) {  validationDescription = value; }
							}
							


							// comma separated list of terms
							String informationElement = terms.toString().trim().replaceAll(" ",",").replaceAll(",,",",");
							//outputLine.put("Information Element", informationElement);

							String outputDes = description;
							if (outputDes==null) { 
								// issue doesn't have a human readable description, create one
								StringBuilder des = new StringBuilder();
								des.append("#").append(Integer.toString(number)).append(" ").append(frameworkClass).append(" "); 
								des.append(resourceType).append(" ");
								des.append(dqDimension).append(": ");
								//des.append(StringUtils.capitalize(termActions.replace("_", " ").toLowerCase()));
								des.append(termActions.replace("_", " ").toLowerCase());

								outputDes = des.toString();
							} 
							if (criterionLabel==null) { 
								StringBuilder des = new StringBuilder();
								des.append(dqDimension).append(": ");
								des.append(termActions.substring(termActions.lastIndexOf("_") + 1).toLowerCase());
								criterionLabel = des.toString();
							}
							//	        		StringBuilder specification = new StringBuilder();
							//	        		if (frameworkClass.equalsIgnoreCase("validation")) { outputDes = validationDescription; }
							//	        		if (frameworkClass.equalsIgnoreCase("amendment")) { outputDes = description; }
							//	        		if (frameworkClass.equalsIgnoreCase("measure")) { outputDes = description; }
							//	        		if (frameworkClass.equalsIgnoreCase("problem")) { outputDes = problemDescription; }
							//	        		if (frameworkClass.equalsIgnoreCase("validation")) { 
							//	        			specification.append("COMPLIANT if ").append(validationDescription)
							//	        			.append(" NOT_COMPLIANT if ").append(problemDescription)
							//	        			.append(" Prereqisites: ").append(outputLine.get("Test Prerequisites"));
							//	        		} else if (frameworkClass.equalsIgnoreCase("problem")) {
							//	        			specification.append("NOT_PROBLEM if ").append(validationDescription)
							//	        			.append(" PROBLEM if ").append(problemDescription)
							//	        			.append(" Prereqisites: ").append(outputLine.get("Test Prerequisites"));	        		
							//	        		} else { 
							//	        			specification.append(outputDes).append(" Prereqisites: ").append(outputLine.get("Test Prerequisites"));
							//	        		}
							outputLine.put("Description", outputDes);
							outputLine.put("ExpectedResponse", specificationDescription );
							outputLine.put("AuthoritiesDefaults", sourceAuthority);
							outputLine.put("Criterion Label", criterionLabel);
							if (frameworkClass.toUpperCase().equals("VALIDATION") || frameworkClass.toUpperCase().equals("ISSUE")) { 
								String criterion = termActions.substring(termActions.indexOf("_")+1);
								logger.debug(criterion);
								if (criterionMap.containsKey(criterion)) { 
									outputLine.put("Criterion",criterionMap.get(criterion));
								} else if (termActions.contains("LESSTHAN_")) { 
									outputLine.put("Criterion",criterionMap.get("CONSISTENT"));
									// special case handling for irregular pattern
								} else if (termActions.contains("_AFTER")) { 
									// special case handling for irregular pattern
									outputLine.put("Criterion",criterionMap.get("CONSISTENT"));
								} else if (termActions.contains("TERRESTRIALMARINE")) { 
									// special case handling for irregular pattern
									outputLine.put("Criterion",criterionMap.get("CONSISTENT"));
								} else if (termActions.contains("_CENTEROFCOUNTRY")) { 
									// special case handling for irregular pattern
									outputLine.put("Criterion",criterionMap.get("LIKELY"));
								} else { 
								    outputLine.put("Criterion","errorunknown");
								}
							}
							if (frameworkClass.toUpperCase().equals("AMENDMENT")) { 
								String enhancement = termActions.substring(termActions.indexOf("_")+1);
								if (enhancementMap.containsKey(enhancement)) { 
									outputLine.put("Enhancement",enhancementMap.get(enhancement));
								} else if (termActions.contains("FROM_")) { 
									// special case handling for alternative pattern
									outputLine.put("Enhancement",enhancementMap.get("FROM"));
								} else { 
								    outputLine.put("Enhancement","errorunknown");
								}
							}
							StringBuilder parameterString = new StringBuilder();
							Iterator<String> iparam =parameters.iterator();
							String paramSeparator = "";
							while (iparam.hasNext()) { 
								parameterString.append(paramSeparator).append(iparam.next());
							}
							outputLine.put("Parameters", parameterString.toString());
							if (includeUseCases) { 
								StringBuilder useCases = new StringBuilder();
								Collection<String> relatedUseCaseColl = useCaseMap.get(testLabel.trim());
								Iterator<String> uci = relatedUseCaseColl.iterator();
								String ucSeparator = "";
								while (uci.hasNext()) {
									useCases = useCases.append(ucSeparator).append(uci.next());
									ucSeparator = ", ";
								}
								outputLine.put("UseCases", useCases.toString());
							}
							if (includeArgumentGuids) { 
								StringBuilder argumentGuids = new StringBuilder();
								String specGuid = outputLine.get("SpecificationGuid");
								if (specGuid.startsWith("urn:uuid:")) { 
									specGuid = specGuid.replace("urn:uuid:", "");
								}
								// List of guids for argument labels + specification guids 
								// Parse sourceAuthority, match bits to arguments
								if (sourceAuthority!=null && sourceAuthority.length()>0) { 
					    			String pattern = "([a-z:a-zA-Z]+) +default *= *(\"[^\"]*\").*";
					    			logger.debug(pattern);
					    			Pattern p = Pattern.compile(pattern);
									if (sourceAuthority.contains(",")) { 
										String[] bits = sourceAuthority.split(",");
										String asep = "";
										for (int j=0; j< bits.length; j++) { 
											String bit = bits[j];
											if (bit.trim().length()>0) { 
												Matcher m = p.matcher(bit);
												if (m.matches()) { 
													String key = "\"Default value for "+m.group(1) + ":" + m.group(2) + "\"" + specGuid;
													if (argumentMap.containsKey(key)) {
														argumentGuids.append(asep).append(argumentMap.get(key));
														asep = ",";
													}
												}
											}
										}
										
									} else { 
						    			Matcher m = p.matcher(sourceAuthority);
						    			logger.debug(Boolean.toString(m.matches()));
						    			if (m.matches()) { 
						    				logger.debug(m.group(1));
						    				logger.debug(m.group(2));
						    				String key = "\"Default value for "+m.group(1) + ":" + m.group(2) + "\"" + specGuid;
						    				logger.debug(key);
						    				if (argumentMap.containsKey(key)) {
						    				  argumentGuids.append(argumentMap.get(key));
						    				}
						    			}
									}
								}
								outputLine.put("ArgumentGuids", argumentGuids.toString());
							}
							
							Iterator<String> iok = outputHeaders.iterator();
							while (iok.hasNext()) {
								String key = iok.next();
								outputPrinter.print(outputLine.get(key));
							}
							outputPrinter.println();
							
							System.out.println("@Provides(value=\"urn:uuid:" + outputLine.get("term_localName")+ "\")");
							System.out.println("@ProvidesVersion(value=\"https://rs.tdwg.org/bdq/terms/" + outputLine.get("term_localName")+ "/" +  outputLine.get("DateLastUpdated") + "\")");
							System.out.println("@"+frameworkClass+"( label = \"" + outputLine.get("Label") + "\", description=\"" + outputDes + "\")");
							System.out.println("@Specification(value=\"" + specificationDescription +"\")");
							System.out.println("");

							if (regenerateMultiRecord) { 
								// Generate MultiRecord measures for each validation 
								if (outputLine.get("Type").toUpperCase().equals("VALIDATION")) { 
									HashMap<String,String>measureLine =  (HashMap<String, String>) outputLine.clone();
									Iterator<String> imkcopy = outputHeaders.iterator();
									while (imkcopy.hasNext()) {
										String key = imkcopy.next();
										measureLine.put(key, outputLine.get(key));
									}
									measureLine.putAll(outputLine);
									// QA measures of completeness
									String forValidation = measureLine.get("Label");
									String origPrefLabel = measureLine.get("prefLabel");
									measureLine.replace("Type", "Measure");
									measureLine.replace("Resource Type","MultiRecord");
									// String now = new SimpleDateFormat("yyyy-MM-dd").format(Calendar.getInstance().getTime());
									// measureLine.replace("DateLastUpdated",now);
									measureLine.replace("Source", "TG2");
									measureLine.replace("Criterion", ""); // not applicable to measures
									measureLine.replace("Enhancement", ""); // not applicable to measures
									String labelStart = "MULTIRECORD_MEASURE_QA";
									measureLine.replace("Label",measureLine.get("Label").toString().replace("VALIDATION", labelStart));
									measureLine.replace("prefLabel", "Measurement over MultiRecord for QualityAssurance of " + origPrefLabel);
									String label = measureLine.get("Label");
									if (measureGuids.containsKey(label) && measuresAllowingIPNM.contains(label))  {
										measureLine.replace("#", "297");  // point to single issue for measuring multirecords for compliance
									} else {  
									 	measureLine.replace("#", "295");  // point to single issue for measuring multirecords for compliance
									}
									measureLine.replace("InformationElement:ActedUpon", "bdq:"+ forValidation + ".Response");
									if (measureGuids.containsKey(label) && measuresAllowingIPNM.contains(label))  {
										measureLine.replace("Specification", "COMPLETE if every " + forValidation + " in the MultiRecord has Response.result=COMPLIANT or Response.status=INTERNAL_PREREQUISITES_NOT_MET, otherwise NOT_COMPLETE." );
										measureLine.replace("Description", "Measure if all " + forValidation + " in a record set are COMPLIANT or INTERNAL_PREREQUISITES_NOT_MET (indicating some empty value)" );
									} else { 
										measureLine.replace("Specification", "COMPLETE if every " + forValidation + " in the MultiRecord has Response.result=COMPLIANT; otherwise NOT_COMPLETE." );
										measureLine.replace("Description", "Measure if all " + forValidation + " in a record set are COMPLIANT" );
									}
									measureLine.replace("Notes", "For Quality Assurance, filter record set until this measure is COMPLETE.");
									measureLine.replace("Parameters", "");
									if (measureGuids.containsKey(label)) {
										measureLine.replace("term_localName", measureGuids.get(label));
										measureLine.replace("term_iri", "https://rs.tdwg.org/bdqcore/terms/" + measureGuids.get(label));
										measureLine.replace("iri", "https://rs.tdwg.org/bdqcore/terms/version/" + measureGuids.get(label) + "-" + updated_at);
									} else { 
										logger.debug(label + " Not Found");
										String mintedGuid =  UUID.randomUUID().toString();
										measureLine.replace("term_localName", mintedGuid);
										measureLine.replace("term_iri", "https://rs.tdwg.org/bdqcore/terms/" + mintedGuid);
										measureLine.replace("iri", "https://rs.tdwg.org/bdqcore/terms/version/" + mintedGuid + "-" + updated_at);
									}
									measureLine.replace("Examples", "");
									measureLine.replace("References", "Veiga AK, Saraiva AM, Chapman AD, Morris PJ, Gendreau C, Schigel D, Robertson TJ (2017). A conceptual framework for quality assessment and management of biodiversity data. PLOS ONE 12(6): e0178731. https://doi.org/10.1371/journal.pone.0178731");;
									measureLine.replace("InformationElement:Consulted","");
									measureLine.replace("Example Implementations (Mechanisms)", "");
									measureLine.replace("Link to Specification Source Code", "");
									Iterator<String> imk = outputHeaders.iterator();
									while (imk.hasNext()) {
										String key = imk.next();
										outputPrinterMeasures.print(measureLine.get(key));
									}
									// QC measures with counts
									outputPrinterMeasures.println();
									measureLine.replace("#", "296");  // point to single issue for multirecord measures with counts
									measureLine.replace("Label",measureLine.get("Label").toString().replace(labelStart, "MULTIRECORD_MEASURE_COUNT_COMPLIANT"));
									measureLine.replace("prefLabel", "Measurement over MultiRecord Counting Compliance of " + origPrefLabel);
									label = measureLine.get("Label");
									if (measureGuids.containsKey(label)) {
										measureLine.replace("term_localName", measureGuids.get(label));
										measureLine.replace("term_iri", "https://rs.tdwg.org/bdqcore/terms/" + measureGuids.get(label));
										measureLine.replace("iri", "https://rs.tdwg.org/bdqcore/terms/version/" + measureGuids.get(label) + "-" + updated_at);
									} else { 
										logger.debug(label + " Not Found");
										String mintedGuid =  UUID.randomUUID().toString();
										measureLine.replace("term_localName", mintedGuid);
										measureLine.replace("term_iri", "https://rs.tdwg.org/bdqcore/terms/" + mintedGuid);
										measureLine.replace("iri", "https://rs.tdwg.org/bdqcore/terms/version/" + mintedGuid + "-" + updated_at);
									}
									measureLine.replace("Specification", "Count the number of " + forValidation + " in the MultiRecord that have Response.result=COMPLIANT." );
									measureLine.replace("Description", "Count the number of " + forValidation + " in a record set that are COMPLIANT" );
									measureLine.replace("Notes", "For Quality Control, compare the Response.result of this measure with the total number of records to assess work needed on the record set.");
									imk = outputHeaders.iterator();
									while (imk.hasNext()) {
										String key = imk.next();
										outputPrinterMeasures.print(measureLine.get(key));
									}
									outputPrinterMeasures.println();
								}
							}

						} else { 
							System.out.println("// #" + Integer.toString(number) + ", not written, lacks a label ");	            
						}
					} // end produce output (has labels specifying test is to be used.
				} // end loop through issues 
				outputPrinter.close();
				if (regenerateMultiRecord) { 
					outputPrinterMeasures.close();
				}

				/**	
	        Set<String> keySet = allkeys.keySet();
	        Iterator<String> iks = keySet.iterator();
	        while (iks.hasNext()) { 
	        	System.out.print(iks.next());
	        	System.out.print("\t");
	        }
	        System.out.println("");
				 */

				os.close();

			} catch (FileNotFoundException e) {
				logger.error(e.getMessage());
			} catch (IOException e) {
				logger.error(e.getMessage());
			}


		} else { 
			System.out.println("Unable to read file " + filename);
		}
	}

}
