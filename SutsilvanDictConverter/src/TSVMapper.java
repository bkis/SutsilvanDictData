import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class TSVMapper {
	
	private Map<String, Integer> header;
	
	//matches "#" and " 1.", " 2." (...)
	private static final String PATTERN_EXCLUDE = "(#|\\s\\d\\.(?=\\s|$))";
	
	//BSP: (di)sfigurar
	private static final String PATTERN_PREFIX = "\\([\\p{L}\\s]+\\)(?\\p{L})";
	
	//BSP: Student -in, student -essa, Student(en)
	private static final String PATTERN_SUFFIX = "-\\p{L}+|(?<=\\p{L})\\(\\p{L}+\\)";
	
	//BSP: (Zauberkünstler), (entstellen)
	private static final String PATTERN_SEMANTIC_1 = "\\s\\([\\p{L}\\s]+\\)(?=(\\s|$))"; 
	
	//BSP: (zool.), (econ. polit.)
	private static final String PATTERN_SEMANTIC_2 = "\\s\\([\\p{L}\\s.]*\\.[\\p{L}\\s.]*\\)(?=(\\s|$))"; 
	
	//BSP: Allianz f  alianza f   
	private static final String PATTERN_GENUS = "\\s(f|m|n|m/f|f/m|m\\(f\\)|m.pl|f.pl|m,f|f,m)(?=\\s)";

	//BSP: anbei adv  aschunto   
	private static final String PATTERN_GRAMMATIK = "\\s(tr|adj|adv|refl|int|tr/int|abs/tr|c/j|interj|\\(refl\\)\\sint|n\\.l|num|prep|cj|subst|adv/prep|pron|pron\\.adj|pron/adj|pron\\.indef|pron/indef|adj/cj|n\\.p)(?=\\s)";
	
//	private static final String PATTERN_FIRST_TOKEN = "^[\\w\\s]+(?=\\s("
//													+ PATTERN_GENUS + "|"
//													+ PATTERN_SEMANTIC_1 + "|"
//													+ PATTERN_SEMANTIC_2 + "|"
//													+ PATTERN_GRAMMATIK + "|"
//													+ "\\s\\s"
//													+ "))";
	
	
	public TSVMapper(){
		header = buildHeader();
	}
	
	public List<String[]> createEntries(String dataPath){
		List<String[]> entries = new ArrayList<String[]>();
		String[] data = readFile(dataPath).split("\n");
		String[] finds;
		String currLine;
		int errorCount = 0;
		int warningsCount = 0;
		
		System.out.println("[INFO] read " + data.length + " lines from " + dataPath);
		
		// add header line
		String[] headerLine = getEmptyEntry();
		for (Entry<String, Integer> e : header.entrySet())
			headerLine[e.getValue()] = e.getKey();
		entries.add(headerLine);
		
		for (String line : data){
			
			// correct errors in source data
			if (line.matches(".*\\([^\\)]*$")) line = line.substring(0, line.length()-1) + ")";
			line = cleanString(line);
			
			
			currLine = line;
			
			// create EMPTY ENTRY
			String[] entry = getEmptyEntry();
			
			
			// EXCLUDE unwanted substrings
			line = line.replaceAll(PATTERN_EXCLUDE, "");
			
			
			// extract and remove GENUS
			finds = getFinds(line, PATTERN_GENUS);
			if (finds.length == 2){
				entry[getFieldIndex("DGenus")] = finds[0].replaceAll(" ", "");
				entry[getFieldIndex("RGenus")] = finds[1].replaceAll(" ", "");
			} else if (finds.length == 1){
				entry[getFieldIndex("DGenus")] = finds[0].replaceAll(" ", "");
				entry[getFieldIndex("RGenus")] = finds[0].replaceAll(" ", "");
			}
			if (finds.length > 0) line = line.replaceAll(PATTERN_GENUS, "");
			
			
			// extract and remove GRAMMATIK
			finds = getFinds(line, PATTERN_GRAMMATIK);
			if (finds.length == 2){
				entry[getFieldIndex("DGrammatik")] = finds[0].replaceAll(" ", "");
				entry[getFieldIndex("RGrammatik")] = finds[1].replaceAll(" ", "");
			} else if (finds.length == 1){
				entry[getFieldIndex("DGrammatik")] = finds[0].replaceAll(" ", "");
				entry[getFieldIndex("RGrammatik")] = finds[0].replaceAll(" ", "");
			}
			if (finds.length > 0) line = line.replaceAll(PATTERN_GRAMMATIK, "");
			
			
			// extract and remove SEMANTIK 1
			finds = getFinds(line, PATTERN_SEMANTIC_1);
			if (finds.length == 2){
				entry[getFieldIndex("DSemantik")] = finds[0];
				entry[getFieldIndex("RSemantik")] = finds[1];
			} else if (finds.length == 1){
				if (line.indexOf(finds[0]) > line.length()/2)
					entry[getFieldIndex("RSemantik")] = finds[0];
				else
					entry[getFieldIndex("DSemantik")] = finds[0];
			}
			if (finds.length > 0) line = line.replaceAll(PATTERN_SEMANTIC_1, "");
			
			
			// extract and remove SEMANTIK 1
			finds = getFinds(line, PATTERN_SEMANTIC_2);
			if (finds.length == 2){
				entry[getFieldIndex("DSubsemantik")] = finds[0];
				entry[getFieldIndex("RSubsemantik")] = finds[1];
			} else if (finds.length == 1){
				if (line.indexOf(finds[0]) > line.length()/2)
					entry[getFieldIndex("RSubsemantik")] = finds[0];
				else
					entry[getFieldIndex("DSubsemantik")] = finds[0];
			}
			if (finds.length > 0) line = line.replaceAll(PATTERN_SEMANTIC_2, "");

			
			// try separating languages
			line = line.replaceAll("\\s+(?=$)", "");
			finds = line.split("\\s{2,}");
			if (finds.length >= 2){
				entry[getFieldIndex("DStichwort")] = finds[0];
				entry[getFieldIndex("RStichwort")] = finds[1];
			} else {
				finds = line.split("\\s");
				if (finds.length == 2){
					entry[getFieldIndex("DStichwort")] = finds[0];
					entry[getFieldIndex("RStichwort")] = finds[1];
				} else if (finds.length > 2){
					entry[getFieldIndex("DStichwort")] = finds[0];
					entry[getFieldIndex("RStichwort")] = line.substring(finds[0].length(), line.length());
					entry[getFieldIndex("Bearbeitungshinweis")] = "WARNING! Original: " + currLine;
					warningsCount++;
				} else {
					entry[getFieldIndex("DStichwort")] = finds[0];
					entry[getFieldIndex("Bearbeitungshinweis")] = "ERROR! Original: " + currLine;
					errorCount++;
					continue; // exclude missing translations
				}
				
			}
			
			
			//TODO ...
			
			
			//add created entry to list
			entries.add(entry);
			
			//DEBUG
			printEntry(entry, line);
		}
		
		System.out.println("[INFO] Finished parsing with " + errorCount + " Errors and " + warningsCount + " Warnings.");
		
		return entries;
	}
	
	
	public void writeSV(List<String[]> svData,
						String delimiter,
						String path){
		
		System.out.print("[INFO] writing data to " + path + " ... ");
		StringBuilder sb = new StringBuilder();
		
		//generate text data
		for (String[] sArr : svData){
			for (String s : sArr){
				sb.append(s + delimiter);
			}
			sb.replace(sb.length()-delimiter.length(), sb.length(), "\n");
		}
		
		//write to file
		try {
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(path), "UTF-8"));
			out.write(sb.toString());
			out.flush();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("OK");
	}

	
	private String readFile(String path){
		System.out.print("[INFO] reading " + path + "... ");
		StringBuffer sb = new StringBuffer();
		
		try {
			FileReader fr = new FileReader(new File(path));
			System.out.print("(Detected File Encoding: " + fr.getEncoding() + ") ");
			int c;
			while ((c = fr.read()) > -1) sb.append((char)c);
			fr.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		System.out.println("OK");
		return sb.toString();
	}
	
	private int getFieldIndex(String fieldName){
		return header.get(fieldName);
	}
	
	private Map<String, Integer> buildHeader(){
		if (this.header != null) return this.header;
		Map<String, Integer> header = new HashMap<String, Integer>();
		header.put("DStichwort", 0); 
		header.put("DSemantik", 1);
		header.put("DSubsemantik", 2);
		header.put("DGrammatik", 3);
		header.put("DGenus", 4);
		header.put("RStichwort", 5);
		header.put("RSemantik", 6);
		header.put("RSubsemantik", 7);
		header.put("RGrammatik", 8);
		header.put("RGenus", 9);
		header.put("Bearbeitungshinweis", 10);
		header.put("redirect_a", 11);
		header.put("redirect_b", 12);
		header.put("maalr_comment", 13);
		header.put("maalr_email", 14);
		header.put("infinitiv", 15);
		header.put("type", 16);
		header.put("subtype", 17);
		header.put("irregular", 18);
		return header;
	}
	
	
	private String[] getFinds(String string, String pattern){
		Pattern p = Pattern.compile(pattern);
		Matcher m = p.matcher(string);
		String finds = "";
		
		while (m.find()){
			finds += m.group() + "qwertzuiopasdfghjklöäyxcvbnm";
		}
		return finds.split("qwertzuiopasdfghjklöäyxcvbnm");
	}
	
	
	private void printEntry(String[] entry, String rest){
		for (String s : entry){
			System.out.print(s + "\t");
		}
		System.out.println();
	}
	
	
	private String[] getEmptyEntry(){
		String[] entry = new String[header.size()];

		for (int i = 0; i < entry.length; i++) {
			entry[i] = "";
		}
		
		return entry;
	}
	
	private String cleanString(String input){
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < input.length(); i++) {
			if ((int)input.charAt(i) != 13)
				sb.append(input.charAt(i));
		}
		return sb.toString();
	}
	
}
