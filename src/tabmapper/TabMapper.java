package tabmapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import conversion.exports.MEIExport;
import conversion.exports.MIDIExport;
import conversion.imports.MIDIImport;
import conversion.imports.TabImport;
import de.uos.fmt.musitech.data.score.NotationChord;
import de.uos.fmt.musitech.data.score.NotationSystem;
import de.uos.fmt.musitech.data.score.NotationVoice;
import de.uos.fmt.musitech.data.structure.Note;
import de.uos.fmt.musitech.utility.math.Rational;
import external.Tablature;
import external.Transcription;
import external.Tablature.Tuning;
import interfaces.CLInterface;
import interfaces.PythonInterface;
import internal.core.Encoding;
import internal.core.ScorePiece;
import internal.core.Encoding.Stage;
import internal.structure.ScoreMetricalTimeLine;
import internal.structure.Timeline;
import tbp.symbols.RhythmSymbol;
import tbp.symbols.TabSymbol.TabSymbolSet;
import tools.ToolBox;
import tools.labels.LabelTools;
import tools.music.PitchKeyTools;
import tools.music.TimeMeterTools;
import tools.text.StringTools;

public class TabMapper {
	private static final int BAR_IND = 0;
	private static final int ONSET_IND = 1;
	private static final int PITCHES_IND = 2;
	public static final int SMALLEST_DUR = Tablature.SRV_DEN;
	private static final int NUM_COURSES = 6;
	private static enum Connection {LEFT, RIGHT};
	private static final String NUM_NOTES_MODEL = "N_model";
	private static final String NUM_NOTES_INTAB = "N_intab";
	private static final String NUM_MISMATCHES = "M";
	private static final String NUM_MISMATCHES_ORN = "M_o";
	private static final String NUM_MISMATCHES_REP = "M_r";
	private static final String NUM_MISMATCHES_FIC = "M_f";
	private static final String NUM_MISMATCHES_ADA = "M_a";
	private static final String M = "m";
	private static final String M_OA = "m_oa";
	private static final String M_A = "m_a"; 
	private static final String P_O = "p_o";
	private static final List<String> COLS = Arrays.asList(
		"piece", // String
		NUM_NOTES_MODEL, NUM_NOTES_INTAB, NUM_MISMATCHES, NUM_MISMATCHES_ORN, 
		NUM_MISMATCHES_REP, NUM_MISMATCHES_FIC, NUM_MISMATCHES_ADA, // int
		M, M_OA, M_A, P_O // double
	);
	private static final String TAB_DIR = "in/tab/";
	private static final String MIDI_DIR = "in/MIDI/";
	private static final String OUT_DIR = "out/";

	private static final List<Integer> MAJOR = Arrays.asList(new Integer[]{0, 2, 4, 5, 7, 9, 11});
	private static final List<Integer> MINOR = Arrays.asList(new Integer[]{0, 2, 3, 5, 7, 8, 10});
	

	// F major example
	// F     G     A  Bb    C     D     E 
	//    F#    Ab       B     C#    Eb
	// 65 66 67 68 69 70 71 72 73 74 75 76 
	// 0     2     4  5     7     9     11
	//
	// #1: 66 for 65 = 1  for 0
	// b2: 66 for 67 = 1  for 2
	// #2: 68 for 67 = 3  for 2
	// b3: 68 for 69 = 3  for 4
	// #4: 71 for 70 = 6  for 5
	// b5: 71 for 72 = 6  for 7 
	// #5: 73 for 72 = 8  for 7
	// b6: 73 for 74 = 8  for 9 
	// #6: 75 for 74 = 10 for 9
	// b7: 75 for 76 = 10 for 11
	//
	private static List<Integer[]> fictaPairsMajor = Arrays.asList(new Integer[][]{
		// Include only notes that are two semitones apart (so not 3rd and 4th and 7th and 1st)
		// First note of pair is ficta
		new Integer[]{1, 0}, // #1st =
		new Integer[]{1, 2}, // b2nd
		new Integer[]{3, 2}, // #2nd = 
		new Integer[]{3, 4}, // b3rd
		// #3rd = 4th / b4th = 3rd
		new Integer[]{6, 5}, // #4th =
		new Integer[]{6, 7}, // b5th
		new Integer[]{8, 7}, // #5th =
		new Integer[]{8, 9}, // b6th
		new Integer[]{10, 9}, // #6th =
		new Integer[]{10, 11}, // b7th
		// #7th = 1st / b1st = 7th 
		// Second note of pair is ficta
		new Integer[]{0, 1}, // #1st =
		new Integer[]{2, 1}, // b2nd
		new Integer[]{2, 3}, // #2nd =
		new Integer[]{4, 3}, // b3rd
		// #3rd = 4th / b4th = 3rd
		new Integer[]{5, 6}, // #4th =
		new Integer[]{7, 6}, // b5th
		new Integer[]{7, 8}, // #5th =
		new Integer[]{9, 8}, // b6th
		new Integer[]{9, 10}, // #6th =
		new Integer[]{11, 10} // b7th
		// #7th = 1st / b1st = 7th
	});

	// D minor example
	// D     E  F     G     A  Bb    C  
	//    Eb       F#    Ab       B     C#
	// 62 63 64 65 66 67 68 69 70 71 72 73
	// 0     2  3     5     7  8     10 
	// 
	// #1: 63 for 62 = 1 for 0
	// b2: 63 for 64 = 1 for 2
	// #3: 66 for 65 = 4 for 3
//	// b4: 66 for 67 = 4 for 5 // #3 is always preferred
	// #4: 68 for 67 = 6 for 5
	// b5: 68 for 69 = 6 for 7
	// #6: 71 for 70 = 9 for 8
	// b7: 71 for 72 = 9 for 10  
	// #7: 73 for 72 = 11 for 10
	// b1: 73 for 74 = 11 for 0
	//
	private static List<Integer[]> fictaPairsMinor = Arrays.asList(new Integer[][]{
		// Include only notes that are two semitones apart (so not 2nd and 3rd and 5th and 6th)
		// First note of pair is ficta
		new Integer[]{1, 0}, // #1st =
		new Integer[]{1, 2}, // b2nd
		// #2nd = 3rd / b3rd = 2nd
		new Integer[]{4, 3}, // #3rd = b4th --> #3rd always preferred
//		new Integer[]{4, 5}, // b4th
		new Integer[]{6, 5}, // #4th =
		new Integer[]{6, 7}, // b5th
		// #5th = 6th / b6th = 5th
		new Integer[]{9, 8}, // #6th =
		new Integer[]{9, 10}, // b7th
		new Integer[]{11, 10}, // #7th =
		new Integer[]{11, 0}, // b1st 
		// Second note of pair is ficta
		new Integer[]{0, 1}, // #1st =
		new Integer[]{2, 1}, // b2nd
		// #2nd = 3rd / b3rd = 2nd
		new Integer[]{3, 4}, // #3rd = b4th --> #3rd always preferred
//		new Integer[]{5, 4}, // b4th
		new Integer[]{5, 6}, // #4th =
		new Integer[]{7, 6}, // b5th
		// #5th = 6th / b6th = 5th
		new Integer[]{8, 9}, // #6th =
		new Integer[]{10, 9}, // b7
		new Integer[]{10, 11}, // #7th =
		new Integer[]{0, 11}, // b1st
	});


	public static void main(String[] args) {
		boolean dev = args.length == 0 ? true : args[CLInterface.DEV_IND].equals(String.valueOf(true));
		Map<String, String> paths = CLInterface.getPaths(dev);
		PythonInterface.setPython(dev, paths.get("VENV_PATH"));

		// Paths
		String tmp = paths.get("TABMAPPER_PATH");
		String inPathTab = StringTools.getPathString(Arrays.asList(tmp, TAB_DIR));
		String inPathMIDI = StringTools.getPathString(Arrays.asList(tmp, MIDI_DIR));
		String outPath = StringTools.getPathString(Arrays.asList(tmp, OUT_DIR));

		// Variables
		Connection connection = Connection.RIGHT;
		boolean includeOrn;
		boolean completeDurations;

		Map<String, String> cliOptsVals = null;
		List<String[]> piecesArr = new ArrayList<>();
		if (args.length > 0) {
			// Parse CLI args and set variables
			List<Object> parsed = CLInterface.parseCLIArgs(
				args, StringTools.getPathString(Arrays.asList(inPathTab))
			);
			cliOptsVals = (Map<String, String>) parsed.get(0);
			List<String> pieces = (List<String>) parsed.get(1);

			includeOrn = cliOptsVals.get(CLInterface.ORNAMENTATION).equals("y") ? true : false;
			completeDurations = cliOptsVals.get(CLInterface.DURATION).equals("y") ? true : false;
			if (pieces.get(0).contains(",")) {
				pieces.forEach(p -> piecesArr.add(
					new String[]{p.split(",")[0], p.split(",")[1], null}
				));
			}
			else {
				pieces.forEach(p -> piecesArr.add(
					new String[]{p, ToolBox.splitExt(p)[0] + MIDIImport.MID_EXT, null}
				));
			}
//			for (String[] s : piecesArr) {
//				System.out.println(Arrays.asList(s));	
//			}
//			System.exit(0);
		}
		else {
			includeOrn = true;
			completeDurations = false;
//			List<String> inputFiles = CLInterface.readInputFolder(
//				inPathTab, TabImport.ALLOWED_FILE_FORMATS, false
//			);
//			inputFiles.forEach(f -> piecesArr.add(new String[]{f, f, null}));
		}
		List<String> piecesNoExt = StringTools.removeExtensions(ToolBox.getItemsAtIndex(piecesArr, 0));

		// Map pieces; add to tables, store output files
		StringBuffer table = new StringBuffer();
		table.append(COLS.stream().collect(Collectors.joining("\t", "", "\r\n")));
		String[][] latexTable = new String[piecesArr.size()+1][COLS.size()];
		List<Integer> intInds = IntStream.rangeClosed(1, COLS.indexOf(M) - 1)
			.boxed()
			.collect(Collectors.toList());
		Integer[] intsToAvg = new Integer[COLS.size()];
		Arrays.fill(intsToAvg, 0);
		Double[] doublesToAvg = new Double[COLS.size()];
		Arrays.fill(doublesToAvg, 0.0);
		List<String> uniqueOrns = new ArrayList<>();
		for (int i = 0; i < piecesArr.size(); i++) {
			// Make local copy of cliOptsVals so that INPUT values do not get overwritten when this 
			// method is called in a loop 
			Map<String, String> cliOptsValsLocal = new LinkedHashMap<>(cliOptsVals);
			
			String[] piece = piecesArr.get(i);
			String tabName = piece[0]; // name of piece, w/ extension. needed for convertToTbp() and ExportMEIFile() (2nd arg) 
			String tabNameNoExt = ToolBox.splitExt(tabName)[0]; // name of piece, w/o extension. needed for Encoding (only setting name)
			String storeName = // needed for all files that are stored (.mei, .mid, .csv, .csv); gets an extension
				Collections.frequency(piecesNoExt, tabNameNoExt) > 1 ? tabName : tabNameNoExt; 
			String modelName = piece[1];
			String shortName = "[" + (i+1) + "]";
			piece[2] = shortName;
			System.out.println("... mapping " + shortName + " " + tabName + " ...");

			// Make tab; make model transcription
			String rawEncoding = TabImport.convertToTbp(inPathTab, tabName, paths);
			Encoding e = new Encoding(rawEncoding, tabNameNoExt, Stage.RULES_CHECKED);
			Tablature tab = new Tablature(e, false);
//			Tablature tab = new Tablature(new File(inPathTab + tabName + Encoding.TBP_EXT));
			
			Transcription model = new Transcription(
				tab.getMeterInfo(), new File(inPathMIDI + modelName)
			);
			// If necessary: adapt maximum number of voices 
			if (model.getNumberOfVoices() == 6) {
				Transcription.setMaxNumVoices(6);
			}
			if (Transcription.MAX_NUM_VOICES == 6 && model.getNumberOfVoices() < 6) {
				Transcription.setMaxNumVoices(5);
			}
			Integer[][] btp = tab.getBasicTabSymbolProperties();
			Integer[][] bnp = model.getBasicNoteProperties();

			// Map tab onto model and calculate results
			List<Object> mapping = map(model, tab, includeOrn, connection);
			List<List<Double>> voiceLabels = (List<List<Double>>) mapping.get(0);
			List<List<Integer>> mismatchInds = (List<List<Integer>>) mapping.get(1);
			List<String> csv = (List<String>) mapping.get(2);
			List<Object> results = getPieceResults(
				btp, bnp, shortName, mismatchInds, includeOrn
			);
			String tableRow = (String) results.get(0);
			Integer[] ints = (Integer[]) results.get(1);
			Double[] doubles = (Double[]) results.get(2);

			// Store
			// a. CSV with mapping statistics
			StringBuffer csvSb = new StringBuffer();
			csv.forEach(s -> csvSb.append(s + "\r\n"));
			ToolBox.storeTextFile(csvSb.toString(), new File(outPath + storeName + "-mapping.csv"));
			// b. MIDI (used to create a GT transcription for training a model)
			if (!includeOrn) {
				List<Integer> repInds = mismatchInds.get(Transcription.REPETITION_IND);
				List<Integer> ornInds = mismatchInds.get(Transcription.ORNAMENTATION_IND);
				List<Integer> ficInds = mismatchInds.get(Transcription.FICTA_IND);
				List<Integer> adaInds = mismatchInds.get(Transcription.ADAPTATION_IND);

				// Remove all voice labels for ornamental notes (which, when includeOrn == false,
				// are null)
				List<List<Double>> voiceLabelsNoOrn = new ArrayList<>();
				for (int j = 0; j < voiceLabels.size(); j++) {
					if (!ornInds.contains(j)) {
//					if (voiceLabels.get(j) != null) {
						voiceLabelsNoOrn.add(voiceLabels.get(j));
					}

				}
				voiceLabels = voiceLabelsNoOrn;

				// Adapt lists to account for excluded ornamental notes (shift indices back) 
				for (List<Integer> l : Arrays.asList(repInds, ficInds, adaInds)) {
					for (int j = 0; j < l.size(); j++) {
						int ind = l.get(j);
						for (int indOrn : ornInds) {
							if (indOrn < ind) {
								l.set(j, l.get(j) - 1);
							}
							else {
								break;
							}
						}
					}
				}
				// Clear ornInds
				ornInds.clear();

				Tablature tabDeorn = new Tablature(tab);
				tabDeorn.augment(
					RhythmSymbol.SEMIMINIM.getDuration(), mismatchInds.get(Transcription.SPECIAL_ORN_IND),
					-1, "deornament"
				);
				tab = tabDeorn;
				btp = tab.getBasicTabSymbolProperties();
			}
			ScorePiece p = new ScorePiece(
				btp, null, voiceLabels, null, model.getScorePiece().getMetricalTimeLine(), 
				model.getScorePiece().getHarmonyTrack(), model.getNumberOfVoices(), 
				model.getScorePiece().getName()
			);
			if (completeDurations) {
				p.completeDurations(Rational.HALF); // TODO OK for all meters?
			}
			File f = new File(outPath + (completeDurations ? storeName + "-dur" : storeName) + MIDIImport.MID_EXT);
			MIDIExport.exportMidiFile(
				p, Arrays.asList(new Integer[]{MIDIExport.GUITAR}), model.getMeterInfo(), 
				model.getKeyInfo(), f.getAbsolutePath()
			);
			// c. MEI (used to visualise the mismatches)
			cliOptsValsLocal = CLInterface.setPieceSpecificTransParams(cliOptsValsLocal, tab, "tabmapper");
			Transcription trans = new Transcription(f);
			MEIExport.exportMEIFile(
				trans, tab, mismatchInds, CLInterface.getTranscriptionParams(cliOptsValsLocal), 
				paths, new String[]{
					outPath + (completeDurations ? storeName + "-dur" : storeName) + MEIExport.MEI_EXT, 
					tabName, 
					"abtab -- tabmapper"
				}
			);
			// d. CSV with ornaments
			List<String> csvOrn = null;
			if (includeOrn) {
				csvOrn = getOrnaments(
					tab, trans, mismatchInds.get(Transcription.ORNAMENTATION_IND)
				);
				StringBuffer csvOrnSb = new StringBuffer();
				csvOrn.forEach(s -> csvOrnSb.append(s + "\r\n"));
				ToolBox.storeTextFile(csvOrnSb.toString(), new File(outPath + storeName + "-ornaments.csv"));
			}

			// Update
			// a. table
			table.append(tableRow);
			// b. latexTable
			for (int j = 0; j < ints.length; j++) {
				latexTable[i][j] = j == 0 ? shortName : 
					(intInds.contains(j) ? String.valueOf(ints[j]) : ToolBox.formatDouble(doubles[j], 0, 5));	
			}
			// c. lists to average
			for (int j = 1; j < ints.length; j++) {
				if (intInds.contains(j)) {
					intsToAvg[j] += ints[j];
				}
				else {
					doublesToAvg[j] += doubles[j];
				}
			}
			// d. uniqueOrns
			if (includeOrn) {
				csvOrn.forEach(s -> { 
					if (!s.startsWith("ornament") && !uniqueOrns.contains(s.substring(0, s.indexOf(",")))) { 
						uniqueOrns.add(s.substring(0, s.indexOf(","))); 
					} 
				});
			}
		}

		// Print
		System.out.println();
		System.out.println(
			piecesArr.size() + (piecesArr.size() == 1 ? " piece (" : " pieces (") + 
			intsToAvg[COLS.indexOf(NUM_NOTES_INTAB)] + " notes) processed"
		);
		piecesArr.forEach(p -> System.out.println(p[2] + " " + p[0]));
		System.out.println();
		System.out.println(table);

		// Store
		StringBuffer uniqueOrnsSb = new StringBuffer();
		uniqueOrns.forEach(s -> uniqueOrnsSb.append(s + "\r\n"));
		ToolBox.storeTextFile(uniqueOrnsSb.toString(), new File(outPath + "ornaments.csv"));
		String fullLatexTable = StringTools.createLaTeXTable(
			latexTable, intsToAvg, doublesToAvg, intInds, 0, 5, true
		);
		ToolBox.storeTextFile(fullLatexTable, new File(outPath + "LaTeX.txt"));
	}


	private static List<Object> getPieceResults(Integer[][] btp, Integer[][] bnp, 
		String shortName, List<List<Integer>> mismatchInds, boolean includeOrn) {

		int numOrn = mismatchInds.get(Transcription.ORNAMENTATION_IND).size();
		int mo = includeOrn ? numOrn : 0;
		int mr = mismatchInds.get(Transcription.REPETITION_IND).size();
		int mf = mismatchInds.get(Transcription.FICTA_IND).size();
		int mad = mismatchInds.get(Transcription.ADAPTATION_IND).size();

		int numNotesTrans = bnp.length;
		int numNotesTab = btp.length - (!includeOrn ? numOrn : 0);
		int numMismatches = mo + mr + mf + mad;

		// m: o, r, f, a count as mismatch
		double m = (numNotesTab - (mo + mr + mf + mad)) / (double) numNotesTab; 
		// m_oa: only o and a count as mismatch
		double moa = (numNotesTab - (mo + mad)) / (double) numNotesTab;
		// m_a: only a count as mismatch
		double ma = (numNotesTab - mad) / (double) numNotesTab; 
		// p_o: percentage of ornamentation
		double po = (mo / (double) numNotesTab);

		String results = String.join("\t", Arrays.stream(new Object[]{
			shortName, numNotesTrans, numNotesTab, numMismatches, mo, mr, mf, mad,
			ToolBox.formatDouble(m, 0, 5), ToolBox.formatDouble(moa, 0, 5), 
			ToolBox.formatDouble(ma, 0, 5), ToolBox.formatDouble(po, 0, 5)})
			.map(String::valueOf).toArray(String[]::new)
		) + "\r\n";

		Integer[] ints = new Integer[COLS.size()];
		Arrays.fill(ints, 0);
		ints[COLS.indexOf(NUM_NOTES_MODEL)] = numNotesTrans;
		ints[COLS.indexOf(NUM_NOTES_INTAB)] = numNotesTab;
		ints[COLS.indexOf(NUM_MISMATCHES)] = numMismatches;
		ints[COLS.indexOf(NUM_MISMATCHES_ORN)] = mo; 
		ints[COLS.indexOf(NUM_MISMATCHES_REP)] = mr;
		ints[COLS.indexOf(NUM_MISMATCHES_FIC)] = mf;
		ints[COLS.indexOf(NUM_MISMATCHES_ADA)] = mad;
		Double[] doubles = new Double[COLS.size()];
		doubles[COLS.indexOf(M)] = m;
		doubles[COLS.indexOf(M_OA)] = moa;
		doubles[COLS.indexOf(M_A)] = ma;
		doubles[COLS.indexOf(P_O)] = po;

		return Arrays.asList(new Object[]{results, ints, doubles});
	}


	private static List<String[]> getPieces(String p) {
		List<String[]> pieces = new ArrayList<>();
		String tcExt = TabImport.TC_EXT;
		String tbpExt = Encoding.TBP_EXT;
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(p))) {
//		try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(path + TAB_DIR))) {
			for (Path entry : stream) {
				if (Files.isRegularFile(entry)) {
					String filename = entry.getFileName().toString();
					String[] ne = ToolBox.splitExt(filename);
					String filenameTbp = ne[0] + tbpExt;
					// Create .tbp file (if necessary)  
					if (filename.endsWith(tcExt) && !Files.exists(Paths.get(p + filenameTbp))) {
						String tbp = null; //TabImport.tc2tbp(new File(entry.toString()));
						ToolBox.storeTextFile(tbp, new File(p + filenameTbp));
					}
					pieces.add(new String[]{ne[0], ne[0]});
//					pieces.add(new String[]{ne[0], ne[0] + "_vm_all"});
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return pieces;
	}


	static List<String> getOrnaments(Tablature tab, Transcription trans, List<Integer> ornIndsFlat) {
		List<String> csv = new ArrayList<>();
		csv.add(String.join(",", Arrays.asList(
			"ornament", "voice", "bar", "pitch"
		)));

		Rational srv = Tablature.SMALLEST_RHYTHMIC_VALUE;
		
		// Split flat list into individual ornaments (series of consecutive integers)
		List<List<Integer>> ornInds = new ArrayList<>();
		List<Integer> orn = new ArrayList<>();
		for (int j = 0; j < ornIndsFlat.size(); j++) {
			orn.add(ornIndsFlat.get(j));
			if (j < ornIndsFlat.size() - 1) { 
				if (ornIndsFlat.get(j + 1) != ornIndsFlat.get(j) + 1) {
					ornInds.add(orn);
					orn = new ArrayList<>();
				}
			}
			else {
				ornInds.add(orn);
			}
		}

		Integer[][] btp = tab.getBasicTabSymbolProperties();
		Integer[][] bnp = trans.getBasicNoteProperties();
		List<Integer[]> ki = trans.getKeyInfo();
		int on = Transcription.ONSET_TIME_NUMER;
		int od = Transcription.ONSET_TIME_DENOM;
		int dn = Transcription.DUR_NUMER;
		int dd = Transcription.DUR_DENOM;
		ScoreMetricalTimeLine smtl = trans.getScorePiece().getScoreMetricalTimeLine();
		NotationSystem ns = trans.getScorePiece().getScore();				
		List<List<Double>> vl = trans.getVoiceLabels();

		List<List<List<Integer>>> aligned = Transcription.alignTabAndTransIndices(btp, bnp);
		List<List<Integer>> tabToTr = aligned.get(0);
		List<List<Integer>> trToTab = aligned.get(1);

		// For each ornamental run
		for (List<Integer> currOrn : ornInds) {
			// The indices in ornInds are tab indices, and need to be aligned to the trans indices
			List<Integer> currOrnTrans = new ArrayList<>();
			currOrn.forEach(ind -> currOrnTrans.add(trToTab.indexOf(Arrays.asList(ind))));
			currOrn = currOrnTrans;

			int currStartInd = currOrn.get(0);
//			currStartInd = trToTab.indexOf(Arrays.asList(currStartInd));
			
			// Get the voice the run is in, as well as the mt, mp, and bar of its first note
			int currVoice = LabelTools.convertIntoListOfVoices(
				vl.get(currStartInd)
			).get(0); // get(0) OK because an ornamental note is never a SNU
			NotationVoice currNv = ns.get(currVoice).get(0);
			Rational currStartMt = new Rational(bnp[currStartInd][on], bnp[currStartInd][od]);
			currStartMt.reduce();
			Rational[] currStartMetPos = smtl.getMetricPosition(currStartMt);
			String bmpStr = TimeMeterTools.getMetricPositionAsString(currStartMetPos);
//			int currStartBar = currStartMetPos[0].getNumer();
//			Rational currStartMp = currStartMetPos[1];
//			currStartMp.reduce();

			List<String> currLoc = Arrays.asList(
				String.valueOf(currVoice),
				bmpStr,
				String.valueOf(bnp[currStartInd][Transcription.PITCH])
//				"voice=" + currVoice, 
//				"bar=" + currStartBar, 
//				"metPos=" + currStartMp,
//				"startPitch=" + bnp[currStartInd][Transcription.PITCH]
			);

			// For each note in the run
			List<String> currEnc = new ArrayList<>();
			for (int j = 0; j < currOrn.size(); j++) {
				int currInd = currOrn.get(j);
				Integer[] currBnp = bnp[currInd];

				// Get pitch, metric time, and duration of current note
				int currPitch = currBnp[Transcription.PITCH];
				Rational currMt = new Rational(currBnp[on], currBnp[od]);
				currMt.reduce();
				Rational currDur = new Rational(currBnp[dn], currBnp[dd]);
				currDur.reduce();
				Rational currOffset = currMt.add(currDur);
				int currIndInNv = currNv.find(currMt);

				// Handle current note (dur)
				Rational currDurRounded = currDur;
				if (!ToolBox.isMultiple(currDur, new Rational(1, SMALLEST_DUR))) {
					currDurRounded = findClosestMultiple(currDur, new Rational(1, SMALLEST_DUR));
				}
				currEnc.add(TimeMeterTools.getDurationAsString(currDurRounded, srv));

				boolean isFirstOfRun = currInd == currOrn.get(0);
				boolean isFirstOfVoice = currNv.find(currMt) == 0;
				boolean isLastOfRun = currInd == currOrn.get(currOrn.size() - 1);
				boolean isLastOfVoice = currNv.find(currMt) == currNv.size() - 1;

				Rational nextMt = null;
				int nextStep = 0;
				String nextDurAsStr = null;
				if (!isLastOfVoice) {
					NotationChord nextNc = currNv.get(currIndInNv + 1);
					int nextPitch = nextNc.get(0).getMidiPitch();
					nextMt = nextNc.get(0).getMetricTime();
					Rational nextDur = nextNc.get(0).getMetricDuration();
					Rational nextDurRounded = nextDur;
					if (!ToolBox.isMultiple(nextDur, new Rational(1, SMALLEST_DUR))) {
						nextDurRounded = findClosestMultiple(nextDur, new Rational(1, SMALLEST_DUR));
					}
					nextDurAsStr = TimeMeterTools.getDurationAsString(nextDurRounded, srv);
					nextStep = nextPitch - currPitch;
				}

				// First note of run: handle left border note (dur, step))
				if (isFirstOfRun) {
					// If there is no previous note in the nv
					if (isFirstOfVoice) {
						currEnc.addAll(0, Arrays.asList("R", "0"));
					}
					// If there is a previous note in the nv
					else {
						NotationChord prevNc = currNv.get(currIndInNv - 1);
						int prevPitch = prevNc.get(0).getMidiPitch();			
						Rational prevMt = prevNc.get(0).getMetricTime();
						Rational prevDur = prevNc.get(0).getMetricDuration();
						Rational prevDurRounded = prevDur;
						if (!ToolBox.isMultiple(prevDur, new Rational(1, SMALLEST_DUR))) {
							prevDurRounded = findClosestMultiple(prevDur, new Rational(1, SMALLEST_DUR));
						}
						String prevDurAsStr = TimeMeterTools.getDurationAsString(prevDurRounded, srv);
						int prevStep = currPitch - prevPitch;

						// If there is a rest between the previous and the current note
						Rational prevOffset = prevMt.add(prevDur);
						if (prevOffset.isLess(currMt)) {
							currEnc.addAll(0, Arrays.asList("R", "0"));
						}
						else {
							currEnc.addAll(0, Arrays.asList(prevDurAsStr, String.valueOf(prevStep)));
						}
					}	
				}
				// Last note of run: handle right border note (step, dur)
				else if (isLastOfRun) {
					// If there is no next note in the nv
					if (isLastOfVoice) {
						currEnc.addAll(Arrays.asList("0", "R"));
					}
					// If there is a next note in the nv
					else {
						// If there is a rest between the current and the next note
						if (currOffset.isLess(nextMt)) {
							currEnc.addAll(Arrays.asList("0", "R"));
						}
						else {
							currEnc.addAll(Arrays.asList(String.valueOf(nextStep), nextDurAsStr));
						}
					}
				}
				// Not last note of run: handle next note (step)
				// NB It is assumed that the run contains no rests
				if (!isLastOfRun) {
					currEnc.addAll(Arrays.asList(String.valueOf(nextStep)));
				}

				// Get (spelled) pitch
				boolean spellPitch = false;
				if (spellPitch) {
					Integer[] currKi = trans.getLocalKeyInfo(currMt);
					String[] pa = (String[]) PitchKeyTools.spellPitch(
						currPitch, currKi[Transcription.KI_KEY], PitchKeyTools.createGrids(
							currKi[Transcription.KI_KEY], currKi[Transcription.KI_MODE]
						), null, -1).get(0);
				}
			}
			csv.add(String.join(" ", currEnc) + "," + String.join(",", currLoc));
		}

		return csv;
	}


	/**
	 * Rounds the given fraction by incrementally decreasing and increasing its numerator
	 * (-1, +1, -2, +2, -3, +3, ...) until the denominator of the resulting reduced fraction 
	 * equals 1.
	 * 
	 * @param r
	 * @return
	 */
	// TESTED
	static Rational roundFraction(Rational r) {
		int numer = r.getNumer();
		int denom = r.getDenom(); 
		int diff = 1;
		while (r.getDenom() != 1) {
			// Try subtraction 
			r = new Rational((numer-diff), denom);
			r.reduce();
			if (r.getDenom() == 1) {
				break;
			}
			// Reset and try addition
			r = new Rational(numer, denom);
			r = new Rational((numer+diff), denom);
			r.reduce();
			if (r.getDenom() == 1) {
				break;
			}
			diff++;
		}
		return r; 
	}


	/**
	 * Maps the notes in the given tablature onto the notes in the given transcription.
	 * 
	 * @param trans
	 * @param tab
	 * @param includeOrnamentation
	 * @param connection
	 * @param shortName
	 * 
	 * @return 
	 */
	private static List<Object> map(Transcription trans, Tablature tab, boolean includeOrnamentation, 
		Connection connection) {
//		System.out.println("\r\n>>> TabMapper.map() called");

		Integer[][] btp = tab.getBasicTabSymbolProperties();
		int numVoices = trans.getNumberOfVoices();
		int srv = Tablature.SMALLEST_RHYTHMIC_VALUE.getDenom();

		// Get key information
		List<Integer[]> keyInfo = trans.getKeyInfo();
		Integer[] firstKey = keyInfo.get(0);
		int firstKeySig = firstKey[Transcription.KI_KEY]; // num b (<0) / # (>0)
		int firstMode = firstKey[Transcription.KI_MODE]; // major (0) / minor (1)
		List<Object> firstGrids = PitchKeyTools.createGrids(firstKeySig, firstMode);

		// Get meter information
		Timeline tl = tab.getEncoding().getTimeline();
		List<Integer[]> meterInfo = tab.getMeterInfo();

		// Set ornamentation threshold to the duration value two levels below beat level
		// n/1: beat level is W; two levels below is Q (RhythmSymbol.MINIM = 24)
		// n/2: beat level is H; two levels below is E (RhythmSymbol.SEMIMINIM = 12)
		// n/4: beat level is Q; two levels below is S (RhythmSymbol.FUSA = 6)
		// n/8: beat level is E; two levels below is T (RhythmSymbol.SEMIFUSA = 3)
		int ornThreshold = RhythmSymbol.MINIM.getDuration() / meterInfo.get(0)[Transcription.MI_DEN];

		List<Integer[][]> gridAndMask = makeGridAndMask(trans, tab);
		Integer[][] grid = gridAndMask.get(0); // notes in MIDI
		Integer[][] mask = gridAndMask.get(1); // notes in tab
//		System.out.println("G R I D");
//		Arrays.stream(grid).forEach(in -> System.out.println(Arrays.asList(in)));		
//		System.out.println("M A S K");	
//		Arrays.stream(mask).forEach(in -> System.out.println(Arrays.asList(in)));
		
		List<List<Double>> voiceLabels = new ArrayList<List<Double>>();
		List<String> csv = new ArrayList<>();
		List<Integer> ornamentationInds = new ArrayList<>();
		List<Integer> repetitionInds = new ArrayList<>();
		List<Integer> fictaInds = new ArrayList<>();
		List<Integer> adaptationInds = new ArrayList<>();
		List<Integer> specialOrnInds = new ArrayList<>();
		List<Integer> activeVoices = new ArrayList<>();
		List<Integer> currOrn = new ArrayList<>();
		int indLastNonOrnChord = -1;
		List<Integer> pitchesTabLastNonOrnChord = null;
		List<List<Double>> voiceLabelsLastNonOrnChord = null;
		boolean lastNonOrnChordContainsTuplet = false;
		List<Integer> pitchesTabLastNonOrnChordWithTuplet = null;
		List<List<Double>> voiceLabelsLastNonOrnChordWithTuplet = null;
		Rational onsetLastOrnChord = null;
		// For each chord
		for (int i = 0; i < grid.length; i++) {
			Integer[] currGrid = grid[i];
			Integer[] currMask = mask[i];

			// Only if the tablature has a note at this onset time
			if (currMask[PITCHES_IND] != null) {
				Rational currOnset = new Rational(currMask[ONSET_IND], SMALLEST_DUR);
				int currDur = currMask[(PITCHES_IND) + NUM_COURSES];
				int chordInd = btp[currMask[(PITCHES_IND) + 2*NUM_COURSES]][Tablature.CHORD_SEQ_NUM];
				String bmp = TimeMeterTools.getMetricPositionAsString(
					tl.getMetricPosition(currMask[ONSET_IND])
				);

				// Get pitches, arranged per voice (low-high), from model; can contain nulls
				// NB: if there is a voice crossing, the list is not sorted
				List<Integer> pitchesModel = Arrays.asList(
					Arrays.copyOfRange(currGrid, PITCHES_IND, PITCHES_IND + numVoices)
				);
				// Get pitches and indices, arranged low-high, from tablature; exclude trailing nulls 
				List<Integer> pitchesTab = Arrays.asList(
					Arrays.copyOfRange(currMask, PITCHES_IND, PITCHES_IND + NUM_COURSES)
				);
				List<Integer> indicesTab = Arrays.asList(
					Arrays.copyOfRange(currMask, PITCHES_IND + 2*NUM_COURSES, PITCHES_IND + 3*NUM_COURSES));
				if (pitchesTab.contains(null)) {
					pitchesTab = pitchesTab.subList(0, pitchesTab.indexOf(null));
					indicesTab = indicesTab.subList(0, indicesTab.indexOf(null));
				}

				// If the chord is ornamental: add note index to currOrn and skip iteration.
				// A chord is ornamental if
				// a. It is a single onset in the tablature AND
				// b. Its duration is less than or equal to the ornamentation threshold AND
				// c. There is no note at the current onset time in the model (i.e., currGrid, 
				//    has only null values after from bar and onset values)
				if ((pitchesTab.size() == 1) && (currDur <= ornThreshold) &&
					Arrays.stream(Arrays.copyOfRange(currGrid, 2, currGrid.length)).allMatch(e -> e == null)) {
					currOrn.add(indicesTab.get(0));
					voiceLabels.add(null);
				}
				// If the chord is non-ornamental: map
				else {
					Integer[] key = keyInfo.size() == 1 ? firstKey : trans.getLocalKeyInfo(currOnset);
					int keySig = keyInfo.size() == 1 ? firstKeySig : key[Transcription.KI_KEY];
					int mode = keyInfo.size() == 1 ? firstMode : key[Transcription.KI_MODE];
					List<Object> grids = keyInfo.size() == 1 ? firstGrids : PitchKeyTools.createGrids(keySig, mode);

					// 1. Do initial mapping (i.e., handle direct matches and ficta)
					List<Object> initialMapping = mapTabChordToMIDI(
						pitchesTab, indicesTab, pitchesModel, keySig, grids, numVoices
					);
					List<List<Integer>> initialIntLists = (List<List<Integer>>) initialMapping.get(0);
					List<Integer> pitchesInMIDI = initialIntLists.get(0);
					List<Integer> indPitchesInMIDI = initialIntLists.get(1);
					List<Integer> pitchesNotInMIDI = initialIntLists.get(2);
					List<Integer> indPitchesNotInMIDI = initialIntLists.get(3);
					List<Integer> nonMappedSNUPitches = initialIntLists.get(4);
					List<Integer> mappedVoices = initialIntLists.get(5);
					List<Integer> currActiveVoices = initialIntLists.get(6);
					List<Integer> extendedSNUVoices = initialIntLists.get(7);
					List<Integer> currFictaInds = initialIntLists.get(8);
					List<Integer> pitchesNotInMIDIOriginal = new ArrayList<Integer>(pitchesNotInMIDI);
					List<List<Double>> voiceLabelsCurrChord = (List<List<Double>>) initialMapping.get(1);

					// Update activeVoices if it does not yet contain all voices
					if (activeVoices.size() < numVoices) {
						currActiveVoices.forEach(v -> { if (!activeVoices.contains(v)) activeVoices.add(v); });
						Collections.sort(activeVoices);
						Collections.reverse(activeVoices);
					}

					if (indPitchesInMIDI.size() != 0) {
						// Make CSV entries for direct matches and ficta
						for (int j = 0; j < indPitchesInMIDI.size(); j++) {
							int ind = indPitchesInMIDI.get(j);
							int pitch = btp[ind][Tablature.PITCH];
							Rational dur = new Rational(btp[ind][Tablature.MIN_DURATION], srv);
							dur.reduce();
							Rational onset = new Rational(btp[ind][Tablature.ONSET_TIME], srv);
							onset.reduce();
							List<Integer> voicesList = LabelTools.convertIntoListOfVoices(
								voiceLabelsCurrChord.get(indicesTab.indexOf(ind))
							);
							String voices = voicesList.stream()
								.map(String::valueOf)
								.collect(Collectors.joining(" and "));
							csv.add(String.join(",", Arrays.stream(new Object[]{
								ind, pitch, dur, onset, 
								chordInd, bmp, voices, "n/a", 
								currFictaInds.contains(ind) ? "ficta" : "match"})
								.map(String::valueOf).toArray(String[]::new)
							));
						}

						if (currFictaInds.size() != 0) {
//							System.out.println(pitchesInMIDI);
//							System.out.println(indPitchesInMIDI);
//							System.out.println(currFictaInds);
//							if (Collections.frequency(currFictaInds, 89) == 2) {
//								currFictaInds = Arrays.asList(89);
//							}
//							System.out.println(currFictaInds);
//							for (int j = 0; j < currFictaInds.size(); j++) {
//								int ind = currFictaInds.get(j);
//								int pitch = btp[ind][Tablature.PITCH];
//
//								// Get voices for ficta note
//								int voice = -1;
//								String pNameTab = ((String[]) PitchKeyTools.spellPitch(
//									pitch, keySig, grids, null).get(0)
//								)[0];
//								for (int k = 0; k < pitchesModel.size(); k++) {
//									if (pitchesModel.get(k) != null) {
//										String pNameModel = ((String[]) PitchKeyTools.spellPitch(
//											pitchesModel.get(k), keySig, grids, null).get(0)
//										)[0];
//										if (pNameTab.equals(pNameModel)) {
//											voice = (numVoices-1) - k;
//											break;
//										}
//									}
//								}
//
//								csv.add(String.join(",", Arrays.stream(new Object[]{
//									ind, pitch, chordInd, bmpStr, voice, "n/a", "ficta"})
//									.map(String::valueOf).toArray(String[]::new)
//								));
//							}

							// Add to lists
							fictaInds.addAll(currFictaInds);
							if (pitchesTab.size() == 1 && currDur <= ornThreshold) {
								specialOrnInds.add(currFictaInds.get(0));
							}
						}
					}

					// 2. Complete mapping (i.e., handle repetitions and adaptations)
					if (voiceLabelsCurrChord.contains(null)) {
						List<Integer[]> cheapestMappingTotal = null;

						// In case of possible consecutive tupletChord
						List<Integer> prevPitches = null;
						List<List<Double>> prevVoiceLabels = null;
						if (lastNonOrnChordContainsTuplet && Arrays.asList(
							new Rational(1, 1), new Rational(1, 2), new Rational(1, 4)).contains(
							currOnset.sub(onsetLastOrnChord))) { // TODO why these values?
							prevPitches = pitchesTabLastNonOrnChordWithTuplet;
							prevVoiceLabels = voiceLabelsLastNonOrnChordWithTuplet;
						}

						List<Object> completedMapping = mapPitchesNotInMIDI(
							pitchesTab, pitchesModel, pitchesNotInMIDI, indPitchesNotInMIDI, 
							pitchesNotInMIDIOriginal, nonMappedSNUPitches, extendedSNUVoices, 
							mappedVoices, voiceLabelsCurrChord, keyInfo, currOnset, trans, 
							prevPitches, prevVoiceLabels
						);
						List<List<Integer>> completedIntLists = (List<List<Integer>>) completedMapping.get(0);
						pitchesNotInMIDI = completedIntLists.get(0);
						indPitchesNotInMIDI = completedIntLists.get(1);
						pitchesNotInMIDIOriginal = completedIntLists.get(2);
						List<Integer> currRepetitionInds = completedIntLists.get(3);
						List<Integer> currAdaptationInds = completedIntLists.get(4);
						voiceLabelsCurrChord = (List<List<Double>>) completedMapping.get(1);
						cheapestMappingTotal = (List<Integer[]>) completedMapping.get(2);

						// Make CSV entries for repetitions and adaptations
						for (int j = 0; j < cheapestMappingTotal.size(); j++) {
							Integer[] in = cheapestMappingTotal.get(j);
							int pitch = in[1];
							int ind = indPitchesNotInMIDI.get(pitchesNotInMIDIOriginal.indexOf(pitch));
							// In case of second unison pitch
							if (Collections.frequency(pitchesNotInMIDIOriginal, pitch) == 2 &&
								ToolBox.getItemsAtIndex(cheapestMappingTotal, 1).lastIndexOf(pitch) == j) {
								ind = indPitchesNotInMIDI.get(pitchesNotInMIDIOriginal.lastIndexOf(pitch));
							}
							Rational dur = new Rational(btp[ind][Tablature.MIN_DURATION], srv);
							dur.reduce();
							Rational onset = new Rational(btp[ind][Tablature.ONSET_TIME], srv);
							onset.reduce();
							int voice = in[0];
							int cost = in[2];
							csv.add(String.join(",", Arrays.stream(new Object[]{
								ind, pitch, dur, onset, 
								chordInd, bmp, voice, cost, 
								currRepetitionInds.contains(ind) ? "repetition" : "adaptation"})
								.map(String::valueOf).toArray(String[]::new)
							));
						}

						// Add to lists
						repetitionInds.addAll(currRepetitionInds);
						adaptationInds.addAll(currAdaptationInds);
						if (pitchesTab.size() == 1 && currDur <= ornThreshold) {
							specialOrnInds.add(
								currRepetitionInds.size() != 0 ? currRepetitionInds.get(0) : currAdaptationInds.get(0)
							);
						}
					}
					voiceLabels.addAll(voiceLabelsCurrChord);

					// 3. Map any preceding ornamental notes still unassigned
					if (!currOrn.isEmpty()) {						
						// Add the ornamental notes to the voice going with the closest pitch
						if (includeOrnamentation) {
							List<Integer> ornPitches = new ArrayList<>();
							currOrn.forEach(ind -> ornPitches.add(btp[ind][Tablature.PITCH]));
							int closestVoice = mapPrecedingOrnamentalNotes(
								ornPitches, pitchesTab, voiceLabelsCurrChord, i, indLastNonOrnChord, 
								pitchesTabLastNonOrnChord, voiceLabelsLastNonOrnChord, connection
							);

							// Make CSV entries for ornamentations
							for (int ind : currOrn) {
								int pitch = btp[ind][Tablature.PITCH];
								Rational dur = new Rational(btp[ind][Tablature.MIN_DURATION], srv);
								dur.reduce();
								Rational onset = new Rational(btp[ind][Tablature.ONSET_TIME], srv);
								onset.reduce();
								int currChordInd = btp[ind][Tablature.CHORD_SEQ_NUM]; 
								String currBmp = TimeMeterTools.getMetricPositionAsString(
									tl.getMetricPosition(btp[ind][Tablature.ONSET_TIME])
								);
								csv.add(String.join(",", Arrays.stream(new Object[]{
									ind, pitch, dur, onset, 
									currChordInd, currBmp, closestVoice, "n/a", 
									"ornamentation"})
									.map(String::valueOf).toArray(String[]::new)
								));
							}

							// Replace voiceLabels in currOrn
							List<Double> vl = LabelTools.createVoiceLabel(
								new Integer[]{closestVoice}, Transcription.MAX_NUM_VOICES
							);
							currOrn.forEach(ind -> voiceLabels.set(ind, vl));
						}
						// Add to list (also if includeOrnamentation == false)
						ornamentationInds.addAll(currOrn);
						currOrn.clear();
					}

					// 4. Set information needed for assignment of any ornamental notes or 
					// consecutive tuplet chords
					indLastNonOrnChord = i;
					pitchesTabLastNonOrnChord = pitchesTab;
					voiceLabelsLastNonOrnChord = voiceLabelsCurrChord;
					if (pitchesTab.size() > 1) {
						for (List<Double> l : voiceLabelsCurrChord) {
							if (Collections.frequency(voiceLabelsCurrChord, l) > 1) {
								lastNonOrnChordContainsTuplet = true;
								pitchesTabLastNonOrnChordWithTuplet = pitchesTab;
								voiceLabelsLastNonOrnChordWithTuplet = voiceLabelsCurrChord;
								onsetLastOrnChord = currOnset;
								break;
							}
						}
					}
				}
			}
		}
		csv = ToolBox.bubbleSortStringList(csv, ",");
		csv.add(0, "note,pitch,duration,onset,chord,bar,mapped voice,cost,category");

		List<List<Integer>> mismatchInds = new ArrayList<>();
		mismatchInds.add(Transcription.INCORRECT_IND, null);
		mismatchInds.add(Transcription.ORNAMENTATION_IND, ornamentationInds);
		mismatchInds.add(Transcription.REPETITION_IND, repetitionInds);
		mismatchInds.add(Transcription.FICTA_IND, fictaInds);
		mismatchInds.add(Transcription.ADAPTATION_IND, adaptationInds);
		mismatchInds.add(Transcription.SPECIAL_ORN_IND, specialOrnInds);

		return Arrays.asList(new Object[]{voiceLabels, mismatchInds, csv});
	}


	/**
	 * Finds the multiple of the given grid value that is the closest to the given onset. 
	 * The Rational returned is cast as a multiple of the grid value's denominator.
	 * 
	 * If the closest multiple above the given onset is equally close as that below, the 
	 * closest multiple above is returned. Example: 17/192 is one away from 16/192 (8/96) 
	 * and from 18/192 (9/96).
	 * 
	 * @param currOnset
	 * @param gridValue
	 * @return
	 */
	// TESTED
	static Rational findClosestMultiple(Rational currOnset, Rational gridValue) {
		// Determine parts before and after decimal point
		int beforeDecPoint = (int) Math.floor(currOnset.toDouble());
		double afterDecPoint = currOnset.toDouble() % 1;

		// Find the closest multiple of gridValue
		double minDist = Double.MAX_VALUE;
		Rational afterDecPointAsRat = null;
		int gridValDen = gridValue.getDenom();
		for (int j = 0; j < gridValDen; j++) {
			Rational currRat = new Rational(j, gridValDen);
			double currDist = Math.abs(afterDecPoint - currRat.toDouble());
			if (currDist < minDist) {
				minDist = currDist;
				afterDecPointAsRat = currRat;
			}
		}
		afterDecPointAsRat.reduce();

		// Correct currOnset
		int den = afterDecPointAsRat.getDenom();
		Rational currOnsetAfter = new Rational(beforeDecPoint*den, den).add(afterDecPointAsRat);
		currOnsetAfter.reduce();

		// Cast as multiple of gridVal. E.g., in the case of 578/3 (and 1/96 as gridVal), both
		// num and den must be multiplied with 96/3 = 32, yielding (578*32)/3*32 = 18496/96
		int multiplier = gridValDen / currOnsetAfter.getDenom(); // NB: this will always give an int because of the rounding done above
		currOnsetAfter = 
			new Rational((currOnsetAfter.getNumer()*multiplier), 
			(currOnsetAfter.getDenom()*multiplier));
		return currOnsetAfter;
	}


	// F I R S T - O R D E R  H E L P E R S
	/**
	 * Returns grid and mask.
	 * 
	 * The grid contains, per chord: bar, onset, pitches per voice (starting at lowest voice), 
	 * durations per voice (starting at lowest voice).
	 * The mask contains, per chord: bar, onset, pitches (low to high), durations (i.e., minimum 
	 * duration), tab note indices
	 * 
	 * @param trans
	 * @param tab
	 * @return
	 */
	private static List<Integer[][]> makeGridAndMask(Transcription trans, Tablature tab) {
		Integer[][] bnp = trans.getBasicNoteProperties();
		Integer[][] btp = tab.getBasicTabSymbolProperties();
		Timeline tl = tab.getEncoding().getTimeline();

		// Determine smallest duration in MIDI (currently simply set to SMALLEST_DUR)
		int smallestDur = -1;
		Rational smallestDurInMIDI = Rational.ONE;
		for (Integer[] b : bnp) {
			Rational currDur = new Rational(b[Transcription.DUR_NUMER], b[Transcription.DUR_DENOM]);
			if (currDur.isLess(smallestDurInMIDI)) {
				smallestDurInMIDI = currDur;
				currDur.reduce();
				smallestDur = currDur.getDenom();
			}
		}
		smallestDur = SMALLEST_DUR;

		// Get union of onset times in tab and trans. Each element of allOnsetTimes contains 
		// at element 0: the actual onset time 
		// at element 1: if the actual onset time is a multiple of 1/SRV, the actual onset time
		//               if not (this can occur in the Transcription only), the actual onset 
		//               time rounded to the closest multiple of 1/SRV
		List<Rational[]> allOnsetTimes = new ArrayList<>();
		// a. Get all onset times in the Transcription
		for (Rational r : trans.getMetricPositionsChords()) {
			// If the onset is not a multiple of smallest dur, it is an imprecise triplet 
			// onset that must rounded to the nearest triplet value
			Rational rounded = r;
			if (!ToolBox.isMultiple(r, new Rational(1, smallestDur))) {
				rounded = findClosestMultiple(r, new Rational(1, smallestDur));
			}
			allOnsetTimes.add(new Rational[]{r, rounded});
		}
		// b. Get all onset times in the Tablature
		for (Rational r : ToolBox.getItemsAtIndex(tab.getMetricTimePerChord(false), 0)) {
//		for (Rational r : tab.getAllOnsetTimes()) {
			// Add only if it is not already in the (rounded) onset times in the Transcription
			if (!ToolBox.getItemsAtIndexRational(allOnsetTimes, 1).contains(r)) {
				allOnsetTimes.add(new Rational[]{r, r});
			}
		}
		// Sort by rounded
		ToolBox.sortByRational(allOnsetTimes, 1);

		// Make grid; initialise with all values set to null  
		NotationSystem score = trans.getScorePiece().getScore();
		int numVoices = score.size();
		Integer[][] grid = new Integer[allOnsetTimes.size()][PITCHES_IND + 2*numVoices];

		// Set bars and onsets
		for (int i = 0; i < allOnsetTimes.size(); i++) {
			Rational onsetFracActual = allOnsetTimes.get(i)[0];
			Rational onsetFracRounded = allOnsetTimes.get(i)[1];
			grid[i][BAR_IND] = 
				tl.getMetricPosition((int) onsetFracActual.mul(Tablature.SRV_DEN).toDouble())[0].getNumer();
//				Utils.getMetricPosition(onsetFracActual, tab.getMeterInfo())[0].getNumer();

			// Set onset, using the rounded value (which is only different from the actual 
			// value if rounding was actually necessary)
			grid[i][ONSET_IND] = onsetFracRounded.mul(smallestDur).getNumer(); // denominator is always 1 because of multiplication with smallest rhythmic value
		}
		// Set pitches and durations
		for (int i = numVoices - 1; i >= 0; i--) {
			NotationVoice nv = score.get(i).get(0);
			for (NotationChord nc : nv) {
				Note n = nc.get(0);
				List<Rational> allOnsetTimesActual = 
					ToolBox.getItemsAtIndexRational(allOnsetTimes, 0);
				int gridRowInd = allOnsetTimesActual.indexOf(n.getMetricTime()); 
				// Add pitch at index of voice i
				grid[gridRowInd][PITCHES_IND + ((numVoices-1)-i)] = n.getMidiPitch();
				// Add duration at index of voice i
				grid[gridRowInd][PITCHES_IND + numVoices + ((numVoices-1)-i)] = 
					n.getMetricDuration().mul(smallestDur).getNumer(); // denominator is always 1 because of multiplication with smallest rhythmic value
			}
		}
					
		// Make mask; initialise with all values set to null
		Integer[][] mask = new Integer[allOnsetTimes.size()][PITCHES_IND + 3*NUM_COURSES];
		
		// Set bars and onsets
		for (int i = 0; i < allOnsetTimes.size(); i++) {
			Rational onsetFrac = allOnsetTimes.get(i)[1];
			mask[i][BAR_IND] = 
				tl.getMetricPosition((int) onsetFrac.mul(Tablature.SRV_DEN).toDouble())[0].getNumer();
//				Utils.getMetricPosition(onsetFrac, tab.getMeterInfo())[0].getNumer();
			mask[i][ONSET_IND] = onsetFrac.mul(smallestDur).getNumer(); // denominator is always 1 because of multiplication with smallest rhythmic value	
		}
		// Set pitches and durations
		for (int i = 0; i < btp.length; i++) {
			int onset = btp[i][Tablature.ONSET_TIME];
			Rational[] posInBar = 
				tl.getMetricPosition(onset);
//				Utils.getMetricPosition(new Rational(onset, smallestDur), tab.getMeterInfo());

//			int bar = posInBar[0].getNumer();
//			Rational pos = posInBar[1];
//			pos.reduce();
//			System.out.println("i = " + i + "; bar " + bar + "; pos " + pos);
			List<Rational> allOnsetTimesRounded = 
				ToolBox.getItemsAtIndexRational(allOnsetTimes, 1);
			Integer[] currRow = mask[allOnsetTimesRounded.indexOf(new Rational(onset, smallestDur))];
			int chordSize = btp[i][Tablature.CHORD_SIZE_AS_NUM_ONSETS];
			for (int j = i; j < i + chordSize; j++) {
				currRow[PITCHES_IND + (j-i)] = btp[j][Tablature.PITCH];
				currRow[PITCHES_IND + NUM_COURSES + (j-i)] = btp[j][Tablature.MIN_DURATION];
				currRow[PITCHES_IND + 2*NUM_COURSES + (j-i)] = j;
			}
			i += (chordSize-1);
		}
		return Arrays.asList(new Integer[][][]{grid, mask});
	}


	/**
	 * Maps the pitches in the tablature chord to a voice by finding their counterpart in the 
	 * MIDI chord. Ficta variations of pitches (Bb vs. B; F# vs. F; etc.) are also considered 
	 * counterparts. 
	 * 
	 * @param pitchesTab
	 * @param indicesTab
	 * @param pitchesGT
	 * @param keySig
	 * @param grids
	 * @param numVoices
	 * 
	 * @return A {@code <List>} containing
	 * <ul>
	 * <li>As element 0: a {@code <List>} of {@code <List>}s, containing
	 * <ul>
	 * <li>As element 0: the tab pitches in the MIDI.</li>
	 * <li>As element 1: the indices of the tab pitches in the MIDI.</li>
	 * <li>As element 2: the tab pitches not in the MIDI.</li>
	 * <li>As element 3: the indices of the tab pitches not in the MIDI.</li>
	 * <li>As element 4: any SNU pitches that could not be mapped due to lack of space in the chord.</li>
	 * <li>As element 5: the mapped voices in the chord.</li>
	 * <li>As element 6: the active voices in the chord.</li>
	 * <li>As element 7: the voices going with any extended SNU.</li>
	 * <li>As element 8: the indices of any pitches flagged as ficta.</li>    
	 * </ul>
	 * </li>
	 * <li>As element 1: a list of voice labels the size of pitchesTab, containing the label for each 
	 *                   mapped pitch and <code>null</code> for each unmapped pitch.</li>
	 * </ul>
	 */
	private static List<Object> mapTabChordToMIDI(List<Integer> pitchesTab, List<Integer> indicesTab, 
		List<Integer> pitchesGT, int keySig, List<Object> grids, int numVoices) {
		List<Double> emptyVoiceLabel = makeEmptyVoiceLabel(numVoices);

		List<Integer> pitchesInMIDI = new ArrayList<>();
		List<Integer> indPitchesInMIDI = new ArrayList<>();
		List<Integer> pitchesNotInMIDI = new ArrayList<>();
		List<Integer> indPitchesNotInMIDI = new ArrayList<>();
		List<Integer> nonMappedSNUPitches = new ArrayList<>();
		List<Integer> mappedVoices = new ArrayList<>();
		List<Integer> activeVoices = new ArrayList<>();
		List<Integer> extendedSNUVoices = new ArrayList<>();
		List<Integer> fictaInds = new ArrayList<>();
		List<List<Double>> voiceLabelsChord = new ArrayList<>();

		for (int i = 0; i < pitchesTab.size(); i++) {
			int pitchInTab = pitchesTab.get(i);
			String[] paTab = (String[]) PitchKeyTools.spellPitch(pitchInTab, keySig, grids, null, -1).get(0);
			String pNameTab = paTab[0];
			int octTab = PitchKeyTools.getOctave(pitchInTab);
			int pitchInd = indicesTab.get(i);

			List<Double> currVoiceLabel = new ArrayList<Double>(emptyVoiceLabel);

			// Map the pitch to (a) voice(s) and create the voice label
			// case		notes tab 	notes MIDI			
			// (a)		1			0		--> unmapped pitch
			// (b)		1			1		--> mapped pitch
			// (c)		1			2		--> mapped SNU
			// (d)		1			> 2		--> extended SNU		
			// (e)		2			0		--> unmapped unison (assumed to be rare)
			// (f)		2			1		--> half-mapped unison
			// (g)		2			2		--> mapped unison
			// (h)		2			> 2		--> extended unison (unison + SNU)
			// NB: In the case of (a) and (e) above, the for-loop below is skipped and 
			// the pitches are added to pitchesNotInMIDI
			for (int j = 0; j < pitchesGT.size(); j++) {
				if (pitchesGT.get(j) != null) {
					int pitchInGT = pitchesGT.get(j);
					String[] paGT = (String[]) PitchKeyTools.spellPitch(pitchInGT, keySig, grids, null, -1).get(0);
					String pNameGT = paGT[0];
					int octGT = PitchKeyTools.getOctave(pitchInGT);
					boolean isFicta = (pitchInGT != pitchInTab && (pNameGT.equals(pNameTab) && octGT == octTab));
					if (pitchInGT == pitchInTab || isFicta) {
						if (isFicta) {
							if (!fictaInds.contains(pitchInd)) { // added 28.08.2025 to avoid inds of SNU ficta being added twice
								fictaInds.add(pitchInd);
							}
						}
						int voice = (numVoices-1) - j;
						int freqInTab = Collections.frequency(pitchesTab, pitchInTab); 
						int freqInGT = Collections.frequency(pitchesGT, pitchInGT); // 09.05.2024
//						int freqInGT = Collections.frequency(pitchesGT, pitchInTab); // 09.05.2024
	
						// Setting byrdAfterCorrectionHalfMapped to true makes initial results 
						// (as on Google doc) incorrect for 
						// IAW: M_a 17 --> 18
						// TLI: M_a  4 -->  5
						// TMI: M_a 18 --> 19
						boolean byrdAfterCorrectionHalfMapped = true; // TODO remove? 
	
						// (b) Mapped pitch (non-SNU and non-unison note)
						if (freqInTab == 1 && freqInGT == 1) {
							currVoiceLabel.set(voice, 1.0);
						}
						// (c) Mapped SNU
						else if (freqInTab == 1 && freqInGT == 2) {
							// If there is space to assign the SNU note to two voices: set voice
							if (pitchesTab.size() < numVoices) {
								currVoiceLabel.set(voice, 1.0);
							}
							// If there is no space to assign the SNU note to two voices, it 'downgrades'
							// from being a SNU note to being a regular note
							// - set voice if pitchInTab is the lower SNU note (i.e., the one that 
							//   comes first in pitchesInGT). (This will be the lower SNU voice; after 
							//   setting, there is no more space to set pitchInTab also to the upper 
							//   SNU voice.)
							// - add pitchInTab to nonMappedSNUPitches if it is not the lower SNU note.
							//   This list is passed to mapPitchesNotInMIDI(), where it is used to correct
							//   mismatches
							// NB This is assumed not to happen in an extended SNU case
							//
							// Example (from D-Mbs_Mus.ms._1512_24v-25r)
							// pitchesInTab = E3 G3 B3 E4
							// pitchesInGT  = E3 E3 B3 E4 (BTAS)
							// There is no space to map the E3 in the tab to both B and T (because then there are 
							// only two voices left for three remaining tab notes), so it is mapped to B only, and 
							// then added to nonMappedSNUPitches
							//
							// Rule: the note added to the tab chord overrules the SNU note being a SNU note 
							// (turns it from a SNU note into a regular note)
							else {
								if (pitchesGT.indexOf(pitchInGT) == j) {
									currVoiceLabel.set(voice, 1.0);
								}
								else {
//									nonMappedSNUPitches.add(pitchInTab);
//									System.out.println("NON-MAPPED SNU CASE --> check code!");
//									System.out.println(indicesTab);
//									System.out.println(pitchesTab);
//									System.out.println(pitchInTab);
//									System.out.println(pitchesGT);
//									System.out.println(pitchInGT);
									if (isFicta) {
										System.out.println("Non-mapped SNU case and ficta--> check code!");
										System.exit(0);
									}
								}
							}
						}
						// (d) Extended SNU (a single note assigned to more than two voices)
						// NB It is assumed that there will always be room for at least one SNU
						else if (freqInTab == 1 && freqInGT > 2) {
							currVoiceLabel.set(voice, 1.0);
						}
						// (f) Half-mapped unison
						else if (freqInTab == 2 && freqInGT == 1 && byrdAfterCorrectionHalfMapped) {
							// Add only if first unison note 
							if (!activeVoices.contains(voice)) {
								currVoiceLabel.set(voice, 1.0);
							}
						}
						// (g) Mapped unison 
						else if (freqInTab == 2 && freqInGT == 2) {
							// Add only if first and last indices in lists align
							if (j == pitchesGT.indexOf(pitchInGT) && i == pitchesTab.indexOf(pitchInTab) || 
								j == pitchesGT.lastIndexOf(pitchInGT) && i == pitchesTab.lastIndexOf(pitchInTab)) { // 09.05.2024
//							if (j == pitchesGT.indexOf(pitchInTab) && i == pitchesTab.indexOf(pitchInTab) || 
//									j == pitchesGT.lastIndexOf(pitchInTab) && i == pitchesTab.lastIndexOf(pitchInTab)) { // 09.05.2024
								currVoiceLabel.set(voice, 1.0);
							}
						}
						// (h) Extended unison (unison + SNU; the second unison note is a SNU)
						// NB: freqInTab can only be 2 in case of a unison
						// NB2: it is assumed that freqInGT == 3
						// Examples:
						// Inviolata, pt. 2, b. 76: f-f-f = 5256_05_...-2, b. 13: f-f
						// Preter, pt.2, b. 143: d-d-d-a  = 5253_02_...-2, b. 56: d-d-a
						//                                = 5694_03_...-2, b. 56: d-d-a
						// Preter, pt.2, b. 164: d-d-d-a  = 5694_03_...-2, b. 77: d-d-a
						// Je ne me puis, b. 54: a-a-a    = 5260_09      , b. 54: a-a-e
						else if (freqInTab == 2 && freqInGT > 2) {
							// Get the last index of the unison note, which is the second index 
							// in pitchesGT
							int secondInd = ToolBox.getIndexOfNthItem(pitchesGT, pitchInGT, 2); // 09.05.2024
//							int secondInd = ToolBox.getIndexOfNthItem(pitchesGT, pitchInTab, 2); // 09.05.2024
							// First and second unison note: add only if first and second indices in lists align
							if (j == pitchesGT.indexOf(pitchInGT) && i == pitchesTab.indexOf(pitchInTab) || 
								j == secondInd && i == pitchesTab.lastIndexOf(pitchInTab)) { // 09.05.2024
//							if (j == pitchesGT.indexOf(pitchInTab) && i == pitchesTab.indexOf(pitchInTab) || 
//									j == secondInd && i == pitchesTab.lastIndexOf(pitchInTab)) { // 09.05.2024
								currVoiceLabel.set(voice, 1.0);
							}
							// Second unison note: add as SNU
							if (i == pitchesTab.lastIndexOf(pitchInTab) && j == pitchesGT.lastIndexOf(pitchInGT)) { // 09.05.2024
//							if (i == pitchesTab.lastIndexOf(pitchInTab) && j == pitchesGT.lastIndexOf(pitchInTab)) { // 09.05.2024
								currVoiceLabel.set(voice, 1.0);
							}
						}
	
						// If the voice has been set: add to mapped voices
						if (currVoiceLabel.get(voice) != 0.0 && !mappedVoices.contains(voice)) {
							mappedVoices.add(voice);
						}
						// Add to active voices if not done yet
						if (currVoiceLabel.get(voice) != 0.0 && !activeVoices.contains(voice)) {
							activeVoices.add(voice);
						}
					}
				}
			}
			// Add the voice label to voiceLabelsCurrChord
			if (!currVoiceLabel.equals(emptyVoiceLabel)) {
				voiceLabelsChord.add(currVoiceLabel);
				pitchesInMIDI.add(pitchInTab);
				indPitchesInMIDI.add(pitchInd);
			}
			else {
				voiceLabelsChord.add(null);
				pitchesNotInMIDI.add(pitchInTab);
				indPitchesNotInMIDI.add(pitchInd);
			}
		}

		// If voiceLabelsChord contains an item with more than two 1s, it represents an 
		// extended SNU
		// NB It is assumed that there will only be one such item
		for (List<Double> l : voiceLabelsChord) {
			if (l != null && Collections.frequency(l, 1.0) > 2) {
				for (int j = 0; j < l.size(); j++) {
					if (l.get(j) == 1.0) {
						extendedSNUVoices.add(j);
					}
				}
			}
		}

		List<List<Integer>> intLists = new ArrayList<>();
		intLists.add(pitchesInMIDI);
		intLists.add(indPitchesInMIDI);
		intLists.add(pitchesNotInMIDI);
		intLists.add(indPitchesNotInMIDI);
		intLists.add(nonMappedSNUPitches);
		intLists.add(mappedVoices);
		intLists.add(activeVoices);
		intLists.add(extendedSNUVoices);
		intLists.add(fictaInds);
		return Arrays.asList(new Object[]{intLists, voiceLabelsChord});
	}


	/**
	 * Maps the pitches in the tablature chord that are not in the MIDI to a voice by finding the 
	 * closest last pitch (in semitones) in all voices. 
	 *
	 * @param pitchesTab
	 * @param pitchesGT
	 * @param pitchesNotInMIDI Returned in adapted form
	 * @param indPitchesNotInMIDI Returned in adapted form
	 * @param pitchesNotInMIDIOriginal Returned in adapted form
	 * @param nonMappedSNUPitches
	 * @param extendedSNUVoices
	 * @param mappedVoices
	 * @param voiceLabelsCurrChord Returned in adapted form
	 * @param keyInfo
	 * @param currOnset
	 * @param trans
	 * @param prevPitches Non-<code>null</code> when the chord is possibly a consecutive
	 *                    tuplet chord.
	 * @param prevVoiceLabels Non-<code>null</code> when the chord is possibly a consecutive
	 *                        tuplet chord.
	 * @return A list containing
	 * <ul>
	 * <li>As element 0: a list of lists, containing
	 * <ul>
	 *               <li>As element 0: the tab pitches not in the MIDI.</li>
	 *               <li>As element 1: the indices of the tab pitches not in the MIDI.</li>
	 *               <li>As element 2: the tab pitches not in the MIDI, in its original (complete) form.</li>
	 *               <li>As element 3: the indices of any pitches flagged as repetition.</li>
	 *               <li>As element 4: the indices of any pitches flagged as other.</li>
	 * </ul>
	 * </li>
	 * <li>As element 1: a list of voice labels the size of pitchesTab, containing the label for each 
	 *                   mapped pitch.</li>
	 * <li>As element 2: a list containing, for each tab pitch not in the MIDI
	 * <ul>
	 *               <li>As element 0: the voice it has been assigned to.</li>
	 *               <li>As element 1: the pitch.</li>
	 *               <li>As element 2: the cost at which it has been assigned to the voice.</li>
	 * </ul>
	 * </li>
	 * </ul>
	 */
	static List<Object> mapPitchesNotInMIDI(List<Integer> pitchesTab, List<Integer> pitchesGT,
		List<Integer> pitchesNotInMIDI, List<Integer> indPitchesNotInMIDI,
		List<Integer> pitchesNotInMIDIOriginal, List<Integer> nonMappedSNUPitches,
		List<Integer> extendedSNUVoices, List<Integer> mappedVoices, List<List<Double>> 
		voiceLabelsCurrChord, List<Integer[]> keyInfo, Rational currOnset, Transcription trans,
		List<Integer> prevPitches, List<List<Double>> prevVoiceLabels){

		int mnv = Transcription.MAX_NUM_VOICES;
		int numVoices = trans.getNumberOfVoices();
		List<Integer> allVoices = 
			IntStream.rangeClosed(0, numVoices-1).boxed().collect(Collectors.toList());
		int numVoicesMappedOnto = 0;
		for (List<Double> l: voiceLabelsCurrChord) {
			if (l != null) {
				numVoicesMappedOnto += (Collections.frequency(l, 1.0));
			}
		}
		
//l		System.out.println("* * * * * * * * * *");
//l		System.out.println("pitchesTab " + pitchesTab);
//l		System.out.println("pitchesGT " + pitchesGT);
//l		System.out.println("pitchesNotInMIDI " + pitchesNotInMIDI);
//l		System.out.println("indPitchesNotInMIDI " + indPitchesNotInMIDI);
//l		System.out.println("pitchesNotInMIDIOriginal " + pitchesNotInMIDIOriginal);
//l		System.out.println("nonMappedSNUPitches " + nonMappedSNUPitches);
//l		System.out.println("extendedSNUVoices " + extendedSNUVoices);
//l		System.out.println("mappedVoices " + mappedVoices);
//l		System.out.println("voiceLabelsCurrChord " + voiceLabelsCurrChord);
//l		System.out.println("* * * * * * * * * *");

		Rational first = new Rational(
			trans.getBasicNoteProperties()[0][Transcription.ONSET_TIME_NUMER],
			trans.getBasicNoteProperties()[0][Transcription.ONSET_TIME_DENOM]
		);
		boolean isFirstChord = first.equals(currOnset);

//		// Assume one key for the whole piece
//		Integer[] key = keyInfo.get(0); 
//		int keySig = key[Transcription.KI_KEY]; // num b (<0) / # (>0)
//		int mode = key[Transcription.KI_MODE]; // major (0) / minor (1)		
//		List<Object> grids = PitchKeyTools.createGrids(keySig, mode);
		
		List<Integer> repetitionInds = new ArrayList<>();
//		List<Integer> fictaInds = new ArrayList<>();
		List<Integer> otherInds = new ArrayList<>();
		
		// Find the cheapest mapping of pitchesNotInMIDI. In three cases, two iterations
		// are needed, and in one special case three; otherwise only one:
		// iteration 0: find the cheapest combination of all availableVoices-sized subsets of 
		//    	        pitchesNotInMIDI and the available voices
		// iteration 1: find the cheapest combination of the remaining pitchesNotInMIDI and _all_ voices
		// iteration 2: repeat iteration 1 (e.g., 5702_benedicta-2, last chord)
		int iterations = 1; 
		if ((pitchesTab.size() > numVoices) || (extendedSNUVoices.size() != 0) || 
			(numVoicesMappedOnto >= numVoices)) {
			iterations = 2;
		}
		if (pitchesNotInMIDI.size() - numVoices - numVoices >= 1) {
			iterations = 3;
		}

		boolean isConsecutiveTupletChord = false;
		List<Integer[]> cheapestMappingTotal = new ArrayList<>();
		for (int iter = 0; iter < iterations; iter++) {
//l			System.out.println("iteration               " + iter);
			// 1. Iteration 0: remove all already mapped voices from availableVoices
			List<Integer> availableVoices = new ArrayList<>(allVoices);
			if (iter == 0) {  
				for (int v : mappedVoices) {
					availableVoices.remove(availableVoices.indexOf(v));
				}
				// If there are no availableVoices: jump to iteration 1
				if (availableVoices.isEmpty()) {
					continue;
				}
			}
//l			System.out.println("LA LA LA available " + availableVoices);

			// 2. For each available voice, get the pitch of the last note before currOnset, 
			// and store voice and pitch together
			List<Integer[]> lastPitchInAvailableVoices = 
				getLastPitchInVoices(availableVoices, numVoices, currOnset, trans);
			List<Integer> activeAvailableVoices = 
				ToolBox.getItemsAtIndex(lastPitchInAvailableVoices, 0);
//l			System.out.println("LA LA LA lastPitchInAvailable");  
//l			for (Integer[] in : lastPitchInAvailableVoices) {
//l				System.out.println("  " + Arrays.asList(in));
//l			}
//l			System.out.println("LA LA LA activeAvailableVoices " + activeAvailableVoices);

			// If there are more pitches not in MIDI than there are active available voices: one 
			// or more voices must be assigned multiple notes. Add voice-pitch pairs for all newly 
			// starting voices, i.e., all mapped voices in the chord that are not active voices,
			// to lastPitchInAvailableVoices
//			// NB: not in case of the first chord, where there are never any active available voices
//			if (!isFirstChord) {
				if (pitchesNotInMIDI.size() > activeAvailableVoices.size() &&
					 Collections.frequency(pitchesNotInMIDI, null) != pitchesNotInMIDI.size() 
					) { // the part after the && is to prevent unnecessary iterations
					if (prevPitches != null && prevPitches.size() == pitchesTab.size()) {
						isConsecutiveTupletChord = true;
						System.out.println("isConsecutiveTupletChord in " + trans.getName());
					}
//l					System.out.println("AIAIAIAIAIA");
	
					for (int j = 0; j < mappedVoices.size(); j++) {
						int mappedVoice = mappedVoices.get(j);
						if (!activeAvailableVoices.contains(mappedVoice)) {
							// Find the voice label in which mappedVoice is set to 1.0 and then the 
							// pitch going with the voice. The elements in pitchesTab correspond to 
							// those in voiceLabelsCurrChord
							int mappedPitch = -1;
							for (int k = 0; k < voiceLabelsCurrChord.size(); k++) {
								List<Double> curr = voiceLabelsCurrChord.get(k);
								if (curr != null && curr.get(mappedVoice) == 1.0) {
									mappedPitch = pitchesTab.get(k);
								}
							}
							lastPitchInAvailableVoices.add(new Integer[]{mappedVoice, mappedPitch});
						}
					}
					lastPitchInAvailableVoices = ToolBox.sortBy(lastPitchInAvailableVoices, 0);
					Collections.reverse(lastPitchInAvailableVoices);
//l					System.out.println("NU NU NU lastPitchInAvailable");  
//l					for (Integer[] in : lastPitchInAvailableVoices) {
//l						System.out.println("  " + Arrays.asList(in));
//l					}
				}
//			}
//-*-			System.out.println("activeAvailableVoices   " + activeAvailableVoices);
//-*-			String lpiav =     "lastPitchInAvlblVoices  ";
//-*-			for (Integer[] in : lastPitchInAvailableVoices) {
//-*-				lpiav += Arrays.toString(in) + " ";
//-*-			}
//-*-			System.out.println(lpiav);

			// 3. Make subsets of pitchesNotInMIDI that are the size of availableVoices
			List<List<Integer>> subsetsOfPitchesNotInMIDI = new ArrayList<>();
			if (pitchesTab.size() > numVoices) {
				// Iteration 0
				if (pitchesNotInMIDI.size() >= availableVoices.size()) {
//l					System.out.println("iter 0");
					subsetsOfPitchesNotInMIDI = 
						ToolBox.getSubsets(pitchesNotInMIDI, availableVoices.size());
//l					System.out.println("pnim " + pitchesNotInMIDI);
//l					System.out.println("av " + availableVoices);
//l					System.out.println(subsetsOfPitchesNotInMIDI);
				}
				// Iteration 1-2, when items have been removed from pitchesNotInMIDI
				if (pitchesNotInMIDI.size() < availableVoices.size()) {
//l					System.out.println("iter 1-2");
					subsetsOfPitchesNotInMIDI.add(pitchesNotInMIDI);
//l					System.out.println("pnim " + pitchesNotInMIDI);
//l					System.out.println("av " + availableVoices);
//l					System.out.println(subsetsOfPitchesNotInMIDI);
				}
			}
			else {
				subsetsOfPitchesNotInMIDI.add(pitchesNotInMIDI);
			}

			// Pad each element of subsetsOfPitchesNotInMIDI with nulls so that it has the size 
			// of lastPitchInAvailableVoices
			for (List<Integer> l : subsetsOfPitchesNotInMIDI) {
				int numNulls = lastPitchInAvailableVoices.size() - l.size();
				for (int j = 0; j < numNulls; j++) { 
					l.add(null);
				}
			}
//-*-			System.out.println("subsetsOfPtchsNotInMIDI " + subsetsOfPitchesNotInMIDI);

			// 4. For each subset of subsetsOfPitchesNotInMIDI: find the cheapest combination and
			// set cheapestMapping. comb contains all possible index combinations of 
			// lastPitchInAvailableVoices and a subset of pitchesNotInMIDI
			int cheapest = Integer.MAX_VALUE;
			List<Integer[]> cheapestMapping = null;
			List<List<Integer[]>> comb = ToolBox.getCombinations(lastPitchInAvailableVoices.size());
			for (int j = 0; j < subsetsOfPitchesNotInMIDI.size(); j++) {
				List<Integer> currSubset = subsetsOfPitchesNotInMIDI.get(j);
				// In case of the first chord, there are no previous voices to compare to, and the pitches
				// in pitchesNotInMIDI (from low to high) are added to the available voices (from low to high). 
				// If there are more pitches than voices, the highest available voice gets more than one pitch 
//				if (isFirstChord) {
//					System.out.println("IF!!");
//					cheapestMapping = new ArrayList<>();
////					Collections.sort(currSubset);
////					Collections.reverse(pitchesNotInMIDI);
//					System.out.println(currSubset);
//					Collections.sort(availableVoices);
//					Collections.reverse(availableVoices);
//					System.out.println(availableVoices);
//					for (int k = 0; k < currSubset.size(); k++) {
//						cheapestMapping.add(new Integer[]{
//							availableVoices.get(k < availableVoices.size() ? k : availableVoices.size() - 1),
//							currSubset.get(k), 
//							-1}
//						);
//					}
//				}
//				else {
//l					System.out.println("ELSE!");
//l					System.out.println(currSubset);
//l					System.out.println(comb.size());
//l					System.out.println("COMB");
//l					System.out.println(lastPitchInAvailableVoices.size());
					List<Integer[]> currCheapestMapping = getCheapestMapping(
						currSubset, comb, lastPitchInAvailableVoices
					);
//l					System.out.println("currCheapestMapping");
//l					System.out.println(currCheapestMapping);
					int currCheapest = 
						ToolBox.sumListInteger(ToolBox.getItemsAtIndex(currCheapestMapping, 2));
					if (currCheapest < cheapest) {
						cheapest = currCheapest;
						cheapestMapping = currCheapestMapping;
					}
//l					currCheapestMapping.forEach(in -> System.out.println(Arrays.asList(in)));
					// If not all voices have been active in the model yet, currSubset
					// can be greater than lastPitchInAvailableVoices. In this case, 
					// the pitch(es) that have not been mapped must be mapped additionally
//					if (currSubset.size() > lastPitchInAvailableVoices.size()) {
////						(currSubset.size() <= lastPitchInAvailableVoices.size() &&	
////						pitchesTab.size() > lastPitchInAvailableVoices.size())) {
//						System.out.println("GODVER");
//						System.out.println();
//						// Get pitch(es) not yet mapped
//						List<Integer> pitchesMapped = ToolBox.getItemsAtIndex(currCheapestMapping, 1);
//						List<Integer> pitchesUnmapped = new ArrayList<>(currSubset);
//						pitchesUnmapped.removeAll(pitchesMapped);
//						System.out.println(pitchesMapped);
//						System.out.println(pitchesUnmapped);
//						// Get voices not yet mapped
//						List<Integer> voicesMapped = ToolBox.getItemsAtIndex(currCheapestMapping, 0);
//						List<Integer> voicesUnmapped = new ArrayList<>(availableVoices);
//						voicesUnmapped.removeAll(voicesMapped);
//						System.out.println(voicesMapped);
//						System.out.println(voicesUnmapped);
//						// Add pitch(es) to unmapped voice(s) (from low to high). If there are more 
//						// pitches than voices, the highest unmapped voice gets more than one pitch
//						for (int k = 0; k < pitchesUnmapped.size(); k++) {
//							cheapestMapping.add(new Integer[]{
//								voicesUnmapped.get(k < voicesUnmapped.size() ? k : voicesUnmapped.size() - 1),
//								pitchesUnmapped.get(k), 
//								-1}
//							);
//						}
//						cheapestMapping.forEach(in -> System.out.println(Arrays.asList(in)));
//					}
//				}
			}
			
//l			System.out.println("- - - - - - - - -");
//l			cheapestMapping.forEach(in -> System.out.println(Arrays.asList(in)));
//l			System.out.println("- - - - - - - - -");
			// In the case of a non-mapped SNU: if this pitch is mapped onto one of the two voices 
			// with the same pitch in the GT, it is not a mismatch and can be removed from cheapestMapping
			// NB In the case of two iterations, only the iteration in which the pitch is mapped 
			// must be checked; this is what the part after the && is for
			List<Integer> currMappedPitches = ToolBox.getItemsAtIndex(cheapestMapping, 1); 
//l			System.out.println(nonMappedSNUPitches);
//l			System.out.println(currMappedPitches);
//			System.exit(0);
			if (nonMappedSNUPitches.size() != 0 && 
				currMappedPitches.contains(nonMappedSNUPitches.get(0))) {
				System.out.println("This happened.");
//				System.exit(0);
				int currNonMappedSNUPitch = nonMappedSNUPitches.get(0); // contains 1 pitch
				int ind = currMappedPitches.indexOf(currNonMappedSNUPitch);
				int currNonMappedSNUVoice = ToolBox.getItemsAtIndex(cheapestMapping, 0).get(ind);
				// If currNonMappedSNUPitch is mapped onto a voice that has the same pitch in the 
				// GT: not a mismatch; correct lists and voice label
				if (pitchesGT.get((numVoices-1)-currNonMappedSNUVoice) == currNonMappedSNUPitch) {
					cheapestMapping.remove(ind);
					// Since the chord has a SNU it cannot have a unison, and currNonMappedSNUPitch 
					// will occur only once in indPitchesNotInMIDI
					// NB pitchesNotInMIDI is padded with nulls; this does not affect indexing
					indPitchesNotInMIDI.remove(pitchesNotInMIDI.indexOf(currNonMappedSNUPitch));
					pitchesNotInMIDI.remove((Integer) currNonMappedSNUPitch);
					pitchesNotInMIDIOriginal = new ArrayList<Integer>(pitchesNotInMIDI);
					if (pitchesNotInMIDIOriginal.contains(null)) {
						pitchesNotInMIDIOriginal = pitchesNotInMIDIOriginal.subList(
							0, pitchesNotInMIDIOriginal.indexOf(null)); 
					}
					voiceLabelsCurrChord.set(
						pitchesTab.indexOf(currNonMappedSNUPitch), 
						LabelTools.convertIntoVoiceLabel(Arrays.asList(new Integer[]{currNonMappedSNUVoice}), mnv));
//-*-					System.out.println("pitchesNotInMIDI        " + pitchesNotInMIDI + " (removal of non-mapped SNU)");
//					System.out.println("pitchesNotInMIDIOrig    " + pitchesNotInMIDIOriginal + " (removal of non-mapped SNU)");
//-*-					System.out.println("indPitchesNotInMIDI     " + indPitchesNotInMIDI + " (removal of non-mapped SNU)");
//-*-					System.out.println("voiceLabelsCurrChord    " + voiceLabelsCurrChord + " (removal of non-mapped SNU)");
				}
			}
			// If the chord is a consecutive tuplet chord: adapt cheapestMapping
			if (isConsecutiveTupletChord) {
//-**-			System.out.println("pitchesTab = " + pitchesTab);				
//-**-				System.out.println("pitchesNotInMIDI = " + pitchesNotInMIDI);
//-**-				System.out.println("prevPitches = " + prevPitches);
//-**-				System.out.println("prevVoiceLabels = " + prevVoiceLabels);
				cheapestMapping.clear();
				for (Integer p : pitchesNotInMIDI) {
					if (p != null) {
						int voice = 
							LabelTools.convertIntoListOfVoices(
							prevVoiceLabels.get(prevPitches.indexOf(p))).get(0);
						// Calculate cost by comparing with the pitch that goes with the 
						// available voice in the MIDI
						int pToCompareWith = -1;
						for (Integer[] in : lastPitchInAvailableVoices) {
							if (in[0] == voice) {
								pToCompareWith = in[1];
								break;
							}
						}
						cheapestMapping.add(new Integer[]{
							voice, p, 
//							-1, // value used for ISMIR 2019 paper 
							Math.abs(p-pToCompareWith)}); 
					}
				}
			}
			cheapestMappingTotal.addAll(cheapestMapping);

			// 6. Add pitch indices to correct list
			for (Integer[] in : cheapestMapping) {
				int pitch = in[1];
				int cost = in[2];
				int pitchInd = indPitchesNotInMIDI.get(pitchesNotInMIDIOriginal.indexOf(pitch));
				// If pitchesNotInMIDIOriginal (and pitchesNotInMIDI -- at this point they are still 
				// the same) contains a unison, pitch appears twice. If repetitionInds or otherInds 
				// already contains pitchInd, pitchInd is the index of the *upper* unison note
				boolean pitchIsInUnison = Collections.frequency(pitchesNotInMIDIOriginal, pitch) == 2 ? true : false;
				int lastPitchInd = indPitchesNotInMIDI.get(pitchesNotInMIDIOriginal.lastIndexOf(pitch));

				// Check for repetition
				if (cost == 0) {
					repetitionInds.add(repetitionInds.contains(pitchInd) ? lastPitchInd : pitchInd);
//					repetitionInds.add(pitchInd);
				}
				// If not repetition: adaptation
				else {
//					boolean possibleSharpFicta = 
//						pitchesGT.contains(pitch-1) && fictaPairs.stream().anyMatch(a -> 
//						Arrays.equals(a, new Integer[]{(pitch-base)%12, ((pitch-1)-base)%12}));
//					boolean possibleFlatFicta = 
//						pitchesGT.contains(pitch+1) && fictaPairs.stream().anyMatch(a ->
//						Arrays.equals(a, new Integer[]{(pitch-base)%12, ((pitch+1)-base)%12}));
					
//					// Check for ficta
//					String pName = null;
//					String pNameOtherNote = null;
//					if (pitchesGT.contains(pitch+1) || pitchesGT.contains(pitch-1)) {
//						// If pitch and pitch +/- 1 have the same pname, ficta applies
//						String[] paPitch = (String[]) PitchKeyTools.spellPitch(
//							pitch, keySig, Arrays.asList(new Object[]{mpcGrid, altGrid, pcGrid}), null
//						).get(0);
//						pName = paPitch[0];
//
//						if (pitchesGT.contains(pitch + 1)) {
//							String[] paSemitoneAbove = (String[]) PitchKeyTools.spellPitch(
//								pitch+1, keySig, Arrays.asList(new Object[]{mpcGrid, altGrid, pcGrid}), null
//							).get(0);
//							pNameOtherNote = paSemitoneAbove[0];
//						}
//						else {
//							String[] paSemitoneBelow = (String[]) PitchKeyTools.spellPitch(
//								pitch-1, keySig, Arrays.asList(new Object[]{mpcGrid, altGrid, pcGrid}), null
//							).get(0);
//							pNameOtherNote = paSemitoneBelow[0];
//						}
//					}
//					if (pName != null && pName.equals(pNameOtherNote)) {
//						fictaInds.add(pitchInd);
//						currOnset.reduce();
//					}
					
					// If not ficta: adaptation
//					else {
					otherInds.add(otherInds.contains(pitchInd) ? lastPitchInd : pitchInd);
//					otherInds.add(pitchInd);
//					}
				}
			}

			// 7. Replace null voice labels with voice labels for unmapped pitches.
			// voiceLabelsCurrChord is aligned with pitchesTab; the element to replace is 
			// determined by finding the index of the unmapped pitch in pitchesTab
			for (Integer[] in : cheapestMapping) {
				int pitch = in[1];
				int voice = in[0];
				List<Double> label = LabelTools.convertIntoVoiceLabel(
					Arrays.asList(new Integer[]{voice}), mnv
				);
				// If unmapped unison (half-mapped unison (see above) is assumed not to happen)						
				if (Collections.frequency(pitchesTab, pitch) == 2) {
					// If first unison note has already been added: add only last unison note
					if (voiceLabelsCurrChord.get(pitchesTab.indexOf(pitch)) != null) {
						voiceLabelsCurrChord.set(pitchesTab.lastIndexOf(pitch), label);
					}
					// If not: add first unison note
					else {
						voiceLabelsCurrChord.set(pitchesTab.indexOf(pitch), label);
					}
				}
				// If unmapped single note
				// NB: unmapped single notes could be SNUs, but are assumed to be not
				else {
					voiceLabelsCurrChord.set(pitchesTab.indexOf(pitch), label);
				}
				// Fix any extended SNU case
				// NB It is assumed that there will only be one item in extendedSNUVoices
				if (extendedSNUVoices.size() != 0) {
					// Find extendedSNU label
					for (List<Double> l : voiceLabelsCurrChord) {
						if (l != null) {
							if (Collections.frequency(l, 1.0) > 2) {
								l.set(voice, 0.0);
							}
						}
					}
				}
			}
//-*-			System.out.println("voiceLabelsCurrChord    " + voiceLabelsCurrChord);

			// 8. Remove pitches mapped to available voices from pitchesNotInMIDI
			for (Integer[] in : cheapestMapping ) {
				pitchesNotInMIDI.remove(pitchesNotInMIDI.indexOf(in[1]));
			}
//-*-			System.out.println("pitchesNotInMIDI        " + pitchesNotInMIDI);
			if (isConsecutiveTupletChord) {
				break;
			}
		}

		// If, because of SNUs, all voices had already been mapped onto before mapping any pitches
		// not in MIDI: remove voices from SNUs to which pitches not in MIDI now have been mapped
		// NB: it is assumed that SNUs and unisons do not occur simultaneously // TODO this actually happens
		if (numVoicesMappedOnto >= numVoices && pitchesNotInMIDIOriginal.size() > 0) {
			for (int p : pitchesNotInMIDIOriginal) {
				int voiceForP = LabelTools.convertIntoListOfVoices(
					voiceLabelsCurrChord.get(pitchesTab.indexOf(p))).get(0);
				for (List<Double> l : voiceLabelsCurrChord) {
					if (l.get(voiceForP) == 1.0 && Collections.frequency(l, 1.0) == 2) {
						l.set(voiceForP, 0.0);
					}
				}
			}
//-*-			System.out.println("voiceLabelsCurrChord    " + voiceLabelsCurrChord + "(removal of voices from SNUs)");
		}

		List<List<Integer>> intLists = new ArrayList<>();
		intLists.add(pitchesNotInMIDI);
		intLists.add(indPitchesNotInMIDI);
		intLists.add(pitchesNotInMIDIOriginal);
		intLists.add(repetitionInds);
//		intLists.add(fictaInds);
		intLists.add(otherInds);	
		return Arrays.asList(new Object[]{intLists, voiceLabelsCurrChord, cheapestMappingTotal});
	}


	/**
	 * Calculates the cheapest connection (in semitones) of the given ornamental run to the  
	 * primary connection chord (which depends on the given Connection). If, due to a unison or 
	 * equidistance to two pitches, there is not a single cheapest connection but two, the 
	 * secondary connection chord is decisive.
	 *    
	 * @param ornPitches
	 * @param pitchesTab
	 * @param voiceLabelsCurrChord
	 * @param indCurrChord
	 * @param indLastNonOrnChord
	 * @param pitchesTabLastNonOrnChord
	 * @param voiceLabelsLastNonOrnChord
	 * @param connection If set to Connection.RIGHT, the chord to the right of the run is 
	 *                   decisive; if set to Connection.LEFT, the chord to its left.
	 * @return
	 */
	static int mapPrecedingOrnamentalNotes(List<Integer> ornPitches, List<Integer> pitchesTab, 
		List<List<Double>> voiceLabelsCurrChord, int indCurrChord, int indLastNonOrnChord, 
		List<Integer> pitchesTabLastNonOrnChord, List<List<Double>> voiceLabelsLastNonOrnChord,
		Connection connection) {

		// If the chord is the first non-ornamental chord in the piece: always connect right
//		boolean isFirstChord = false;
		if (pitchesTabLastNonOrnChord == null) {
//			isFirstChord = true;
			connection = Connection.RIGHT;
		}	
		
		int firstOrnPitch = ornPitches.get(0);
		int lastOrnPitch = ornPitches.get(ornPitches.size()-1);

		// Determine primary and secondary connection chord 
		int primaryOrnPitch = connection == Connection.RIGHT ? lastOrnPitch : firstOrnPitch;
		int secondaryOrnPitch = connection == Connection.RIGHT ? firstOrnPitch: lastOrnPitch;  
		List<Integer> primaryPitchesTab = 
			connection == Connection.RIGHT ? pitchesTab : pitchesTabLastNonOrnChord; 
		List<Integer> secondaryPitchesTab = 
			connection == Connection.RIGHT ? pitchesTabLastNonOrnChord : pitchesTab;
		List<List<Double>> primaryVoiceLabels = 
			connection == Connection.RIGHT ? voiceLabelsCurrChord : voiceLabelsLastNonOrnChord;
		List<List<Double>> secondaryVoiceLabels = 
			connection == Connection.RIGHT ? voiceLabelsLastNonOrnChord : voiceLabelsCurrChord;
		int secondaryIndChord = connection == Connection.RIGHT ? indLastNonOrnChord : indCurrChord; 
//		if (connection == Connection.RIGHT) {
//			primaryOrnPitch = lastOrnPitch;
//			secondaryOrnPitch = firstOrnPitch;
//			primaryPitchesTab = pitchesTab;
//			secondaryPitchesTab = pitchesTabLastNonOrnChord;
//			primaryVoiceLabels = voiceLabelsCurrChord;
//			secondaryVoiceLabels = voiceLabelsLastNonOrnChord;
//			secondaryIndChord = indLastNonOrnChord; 
//		}
//		else {
//			primaryOrnPitch = firstOrnPitch;
//			secondaryOrnPitch = lastOrnPitch;
//			primaryPitchesTab = pitchesTabLastNonOrnChord;
//			secondaryPitchesTab = pitchesTab;
//			primaryVoiceLabels = voiceLabelsLastNonOrnChord;
//			secondaryVoiceLabels = voiceLabelsCurrChord;
//			secondaryIndChord = indCurrChord;
//		}

		// Find the closest pitch(es) in the primary connection chord. If it contains a unison, 
		// a SNU, or if primaryOrnPitch is equally far from two notes in it, two pitches are 
		// returned
		List<List<Integer>> closestToPrimary = 
			findClosestPitchesAndVoices(primaryOrnPitch, primaryPitchesTab, primaryVoiceLabels);
//		List<List<Integer>> closest = 
//			findClosestPitchesAndVoices(lastOrnPitch, pitchesTab, voiceLabelsCurrChord);
		List<Integer> closestPitchesPrimary = closestToPrimary.get(0);
		List<Integer> closestVoicesPrimary = closestToPrimary.get(1);
//-*-		System.out.println("closestPitchesPrimary   " + closestPitchesPrimary);
//-*-		System.out.println("closestVoicesPrimary    " + closestVoicesPrimary);

		// Determine closest voice
		int closestVoice = -1;
		// If the closest pitch occurs only once
		if (closestVoicesPrimary.size() == 1) {
			closestVoice = closestVoicesPrimary.get(0); 
		}
		// If the closest pitch occurs more than once (SNU or unison): check the secondary 
		// connection chord. Only do this if the chord is not the first non-ornamental chord in 
		// the piece, in which case there is no secondary connection chord
		else if (closestVoicesPrimary.size() > 1 && pitchesTabLastNonOrnChord != null) {
			List<List<Integer>> closestSecondary = 
				findClosestPitchesAndVoices(secondaryOrnPitch, secondaryPitchesTab, 
				secondaryVoiceLabels);
//			List<List<Integer>> closestPrev = 
//				findClosestPitchesAndVoices(firstOrnPitch, pitchesTabLastNonOrnChord, 
//				voiceLabelsLastNonOrnChord);

//			// Only if the chord is not the first chord in the connectRight case, in which case
//			// there is no secondary chord
//			if (!(connection == Connection.RIGHT && isFirstChord)) {
//			if (closestSecondary != null) {
			List<Integer> closestPitchesSecondary = closestSecondary.get(0);
			List<Integer> closestVoicesSecondary = closestSecondary.get(1);
//-*-			System.out.println("secondaryIndChord       " + secondaryIndChord);
//-*-			System.out.println("secondaryPitchesTab     " + secondaryPitchesTab);
//-*-			System.out.println("secondaryVoiceLabels    " + secondaryVoiceLabels);
//-*-			System.out.println("closestPitchesSecondary " + closestPitchesSecondary);
//-*-			System.out.println("closestVoicesSecondary  " + closestVoicesSecondary);

			if (closestVoicesSecondary.size() == 1) {
				if (closestVoicesPrimary.contains(closestVoicesSecondary.get(0))) {
					closestVoice = closestVoicesSecondary.get(0);
				}
			}
			if (closestVoicesSecondary.size() == 2) {
				if (closestVoicesPrimary.contains(closestVoicesSecondary.get(0))) {
					closestVoice = closestVoicesSecondary.get(0);
				}
				else if (closestVoicesPrimary.contains(closestVoicesSecondary.get(1))) {
					closestVoice = closestVoicesSecondary.get(1);
				}
			}
//			}			
		}
		
		// If closestVoice still has not been set, which is the case (1) if the chord is the
		// first non-ornamental chord and the closest pitch occurs more than once, or (2) if 
		// closestVoicesSecondary are altogether different from closestVoices: set to first of 
		// closestVoicesPrimary
		if (closestVoice == -1) {
			closestVoice = closestVoicesPrimary.get(0); // TODO
		}

		return closestVoice;
	}
	
	
	// S E C O N D - O R D E R  H E L P E R S
	private static List<Double> makeEmptyVoiceLabel(int numVoices) {
		List<Double> emptyVoiceLabel = new ArrayList<Double>();
		for (int k = 0; k < 6; k++) {
			if (k < 5) {
				emptyVoiceLabel.add(0.0);
			}
			if (k == 5 && numVoices == 6) {
				emptyVoiceLabel.add(0.0);
			}
		}
		return emptyVoiceLabel;
	}


	/**
	 * Gets, for each voice in the given voices, the pitch of the last note before the given onset 
	 * in the given <code>Transcription</code>.
	 * 
	 * @param availableVoices
	 * @param numVoices
	 * @param onset
	 * @param trans
	 * @return Per given voice (starting at the lowest) an Integer[] containing
	 *         as element 0: the voice 
     *         as element 1: the last pitch in that voice
	 */
	// TESTED
	static List<Integer[]> getLastPitchInVoices(List<Integer> availableVoices, int numVoices,
		Rational onset, Transcription trans) {
		List<Integer[]> lastPitchInAvailableVoices = new ArrayList<>();
		NotationSystem ns = trans.getScorePiece().getScore();
		for (int j = numVoices - 1; j >= 0; j--) {
			if (availableVoices.contains(j)) {
				NotationVoice nv = ns.get(j).get(0);
//				Integer[] curr = null;
				for (NotationChord nc : nv) {
					Note prev = Transcription.getAdjacentNoteInVoice(nv, nc.get(0), true);
					if (nc.size() == 2) {
						System.out.println("NotationChord has size 2");
						System.out.println(nc);
						System.exit(0);
					}
					if (prev != null) {
						Note noteAfterPrev = Transcription.getAdjacentNoteInVoice(nv, 
							prev, false);
//						// prev comes before current note; the note after prev is either the
//						// note at currOnset (in which case prev is not tied over currOnset)
//						// or the first note after currOnset (in which case prev is tied over)
//						if (prev.getMetricTime().isLess(currOnset) && 
//							noteAfterPrev.getMetricTime().isGreaterOrEqual(currOnset)) {
//							lastPitchInAvailableVoices.add(new Integer[]{j, prev.getMidiPitch()});
//							break;
//						}
						// prev comes before current note
						if (prev.getMetricTime().isLess(onset)) {
							// If the note after prev is the note at currOnset (in which case 
							// prev is not tied over currOnset) or the first note after currOnset 
							// (in which case prev is tied over) or non-existant
							if (noteAfterPrev.getMetricTime().isGreaterOrEqual(onset) || noteAfterPrev == null) {
//								curr = new Integer[]{j, prev.getMidiPitch()};
								lastPitchInAvailableVoices.add(new Integer[]{j, prev.getMidiPitch()});
								break;
							}
							// If the note after prev is the last note in the voice, i.e., if there is 
							// no note on or after currentOnset
							if (noteAfterPrev.equals(nv.get(nv.size()-1).get(0))) {
//								curr = new Integer[]{j, noteAfterPrev.getMidiPitch()};
								lastPitchInAvailableVoices.add(new Integer[]{j, noteAfterPrev.getMidiPitch()});
								break;
							}
						}
					}
				}
//				lastPitchInAvailableVoices.add(curr);
			}
		}
		return lastPitchInAvailableVoices;
	}


	/**
	 * Given all possible combinations, finds the cheapest mapping of the given list of pitches to 
	 * the voices in the given list of last pitches in all available voices.
	 *
	 * Example:
	 * lastPitchInAvailableVoices = [[3, 48], [1, 62]]; pitches = [50, 60]
	 * mapping pitches [50, 60] to voices [3, 1] is the cheapest:
	 * cost for mapping pitches [50, 60] to voices [3, 1] = |50-48| + |60-62| = 4
	 * cost for mapping pitches [50, 60] to voices [1, 3] = |50-62| + |60-48| = 24
	 * 
	 * If lastPitchInAvailableVoices contains a SNU, the mapping to the lower voice is taken.
	 *
	 * @param pitches
	 * @param comb
	 * @param lastPitchInAvailableVoices
	 * @return A List<Integer[]> representing the cheapest mapping for all pitches, each element 
	 *         of which contains
	 *         <ul>
	 *         <li>As element (0): the voice the pitch is mapped to.</li>
	 *         <li>As element (1): the pitch.</li> 
	 *         <li>As element (2): the cost (in semitones) of mapping the pitch to the voice.</li>
	 *         </ul>
	 */
	// TESTED
	static List<Integer[]> getCheapestMapping(List<Integer> pitches, List<List<Integer[]>> comb, 
		List<Integer[]> lastPitchInAvailableVoices) {
//l		System.out.println("--> " + pitches);
//l		if (pitches.equals(Arrays.asList(59, null))) {
//l			System.out.println("JAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
//l			lastPitchInAvailableVoices.forEach(in -> System.out.println(Arrays.asList(in)));
//l		}
		List<Integer[]> cheapestMapping = null;
		int cheapest = Integer.MAX_VALUE;
		for (int j = 0; j < comb.size(); j++) {
			List<Integer[]> currComb = comb.get(j);
//			int currCost = 0;
			List<Integer[]> currMapping = new ArrayList<>();
			// Each combination c contains
			// at index 0: the index in lastPitchInAvailableVoices (gives [voice, pitch])
			// at index 1: the index in pitchesNotInMIDI (gives [pitch])
			for (Integer[] c : currComb) {
				int firstListInd = c[0];
				int secondListInd = c[1];
				if (pitches.get(secondListInd) != null) {
					int currPitchNotInMIDI = pitches.get(secondListInd);
					int currCost = Math.abs(lastPitchInAvailableVoices.get(firstListInd)[1] -
						currPitchNotInMIDI);
//					currCost += Math.abs(lastPitchInAvailableVoices.get(firstListInd)[1] -
//						currPitchNotInMIDI);
					currMapping.add(new Integer[]{
						lastPitchInAvailableVoices.get(firstListInd)[0], 
						currPitchNotInMIDI, 
						currCost
					});
//					currMapping.add(new Integer[]{
//							lastPitchInAvailableVoices.get(firstListInd)[0], currPitchNotInMIDI});
				}
			}
			int currTotalCost = ToolBox.sumListInteger(ToolBox.getItemsAtIndex(currMapping, 2));

			if (currTotalCost <= cheapest) {
				if (currTotalCost < cheapest) {
					cheapest = currTotalCost;
					cheapestMapping = currMapping;
				}
				// In case of a tie, prefer the mapping that has more repetitions (if any)
				else if (currTotalCost == cheapest) {
					int numZerosInCheapest = Collections.frequency(ToolBox.getItemsAtIndex(cheapestMapping, 2), 0);
					int numZerosInCurr = Collections.frequency(ToolBox.getItemsAtIndex(currMapping, 2), 0);
					if (numZerosInCurr > numZerosInCheapest) {
						cheapest = currTotalCost;
						cheapestMapping = currMapping;
					}
				}

//				if (currCheapest == 0) {
//					numOfTieRepetitions++;
//				}
//				for (Integer[] in : currMapping) {
//					if (in[2] == 0) {
//						numOfTieRepetitions++;
//					}
//				}
			}
		}
		return cheapestMapping;
	}


	/**
	 * Given the index of an ornamental note, returns 
	 * (i)  the pitches in the given chord that are closest to the note at that index
	 * (ii) the voices going with those pitches
	 * 
	 * @param lastOrnPitch
	 * @param pitchesTab
	 * @param voiceLabels
	 * @return
	 */
	// TESTED
	static List<List<Integer>> findClosestPitchesAndVoices(int lastOrnPitch, 
		List<Integer> pitchesTab, List<List<Double>> voiceLabels) {
		List<List<Integer>> pitchesAndVoices = new ArrayList<>();
		
		if (pitchesTab == null) {
			return null;
		}
		else {
			int smallest = Integer.MAX_VALUE;
			List<Integer> closestPitches = new ArrayList<>();
			for (int j = 0; j < pitchesTab.size(); j++) {
				int diff = Math.abs(pitchesTab.get(j) - lastOrnPitch); 
				if (diff < smallest) {
					smallest = diff;
					closestPitches.clear();
					closestPitches.add(pitchesTab.get(j));
				}
				else if (diff == smallest) {
					closestPitches.add(pitchesTab.get(j));
				}
			}
			// Find the voice(s) going with the closest pitch(es) (voiceLabelsCurrChord
			// is aligned with pitchesTab)
			List<Integer> closestVoices = new ArrayList<>();
			for (int j = 0; j < pitchesTab.size(); j++) {
				if (closestPitches.contains(pitchesTab.get(j))) {
//				if (pitchesTab.get(j) == closestPitch) {
					closestVoices.addAll(
						LabelTools.convertIntoListOfVoices(voiceLabels.get(j)));
				}
			}
			pitchesAndVoices.add(closestPitches);
			pitchesAndVoices.add(closestVoices);
			return pitchesAndVoices;
		}
	}


	// C O N V E N I E N C E  M E T H O D S
	private static List<String[]> getPiecesOLD() {
		List<String[]> pieces = Arrays.asList(new String[][]{
			// Two Morales test pieces from https://www.uma.es/victoria/morales.html
//			new String[] {"3610_033_inter_natos_mulierum_morales_T-rev", "Morales-Inter_Natos_Mulierum-1-54"},
//			new String[] {"3618_041_benedictus_from_missa_de_l_homme_arme_morales_T", "Morales-Lhomme_Arme-a4-5-Benedictus"},
			
			// Tours
//			new String[]{"1132_13_o_sio_potessi_donna_berchem_solo", "Berchem_-_O_s'io_potessi_donna"}
//			new String[]{"capirola-1520-et_in_terra_pax", "Jos0403b-Missa_Pange_lingua-Gloria-Et_in_terra"},
			
			// JosquIntab
			// a. Mass sections
//			new String[]{"4471_40_cum_sancto_spiritu", "Jos0303b-Missa_De_beata_virgine-Gloria-222-248"}, // tab bar:metric bar 3:2
//			new String[]{"5266_15_cum_sancto_spiritu_desprez", "Jos0303b-Missa_De_beata_virgine-Gloria-222-248"}, // tab bar:metric bar 3:2
//			new String[]{"3643_066_credo_de_beata_virgine_jospuin_T-1", "Jos0303c-Missa_De_beata_virgine-Credo-1-102"},
//			// JEP (has imprecise triplet onset(s))
//			new String[]{"3643_066_credo_de_beata_virgine_jospuin_T-2", "Jos0303c-Missa_De_beata_virgine-Credo-103-159"},
//			new String[]{"5106_10_misa_de_faysan_regres_2_gloria", "Jos0801b-Missa_Faisant_regretz-Gloria-37-94"},
//			new String[]{"5189_16_sanctus_and_hosanna_from_missa_faisant_regrets-1", "Jos0801d-Missa_Faisant_regretz-Sanctus-1-22"},
//			new String[]{"5107_11_misa_de_faysan_regres_pleni", "Jos0801d-Missa_Faisant_regretz-Sanctus-23-67"},
//			new String[]{"5189_16_sanctus_and_hosanna_from_missa_faisant_regrets-2", "Jos0801d-Missa_Faisant_regretz-Sanctus-68-97"},
//			new String[]{"5188_15_sanctus_and_hosanna_from_missa_hercules-1", "Jos1101d-Missa_Hercules_dux_Ferrarie-Sanctus-1-17"},
//			new String[]{"3584_001_pleni_missa_hercules_josquin", "Jos1101d-Missa_Hercules_dux_Ferrarie-Sanctus-18-56"},
//			new String[]{"5188_15_sanctus_and_hosanna_from_missa_hercules-2", "Jos1101d-Missa_Hercules_dux_Ferrarie-Sanctus-57-88"},
//			new String[]{"3585_002_benedictus_de_missa_pange_lingua_josquin", "Jos0403d-Missa_Pange_lingua-Sanctus-139-186"},
//			new String[]{"5190_17_cum_spiritu_sanctu_from_missa_sine_nomine", "Jos1202b-Missa_Sine_nomine-Gloria-103-132"},

			// b. Motets
			// 5254_03_benedicta_es_coelorum_desprez-1 has 3(Q) triplets in bb. 23, 27, 70
			// 5263_12_in_exitu_israel_de_egipto_desprez-3 has 3(Q) triplets in bb. 74-79
			// 5256_05_inviolata_integra_desprez-2 has 3(Q) triplets in b. 19
			// 5256_05_inviolata_integra_desprez-3 has 3(Q) triplets in bb. 21-22
			// 4465_33-34_memor_esto-2 has 3(Q) triplets in bb. 64-74, 100-102, 109-113
			// 5255_04_stabat_mater_dolorosa_desprez-2 has 3(Q) triplets in bb. 71-75, 77, 79-81, 83-85
//			// JEP (has imprecise triplet onset(s))		
//			new String[]{"5265_14_absalon_fili_me_desprez", "Jos1401-Absalon_fili_mi"},
//			// JEP (has imprecise triplet onset(s))		
//			new String[]{"3647_070_benedicta_est_coelorum_josquin_T", "Jos2313-Benedicta_es_celorum-1-107"},
//			// JEP (has imprecise triplet onset(s))
//			new String[]{"4964_01a_benedictum_es_coelorum_josquin", "Jos2313-Benedicta_es_celorum-1-107"},
//			new String[]{"4965_01b_per_illud_ave_josquin", "Jos2313-Benedicta_es_celorum-108-135"},
//			new String[]{"4966_01c_nunc_mater_josquin", "Jos2313-Benedicta_es_celorum-136-176"},
//			// JEP (has imprecise triplet onset(s))
//			new String[]{"5254_03_benedicta_es_coelorum_desprez-1", "Jos2313-Benedicta_es_celorum-1-107"}, // check triplets in tab 
//			new String[]{"5254_03_benedicta_es_coelorum_desprez-2", "Jos2313-Benedicta_es_celorum-108-135"},
//			new String[]{"5254_03_benedicta_es_coelorum_desprez-3", "Jos2313-Benedicta_es_celorum-136-176"},
//			// TODO 0, 1, 32, 33, 36, 38	
//			// JEP (has imprecise triplet onset(s))
//			new String[]{"5702_benedicta-1", "Jos2313-Benedicta_es_celorum-1-107"},
//			// TODO 91, 93, 222, 226, 231, 234
//			new String[]{"5702_benedicta-2", "Jos2313-Benedicta_es_celorum-108-135"},
//			new String[]{"5702_benedicta-3", "Jos2313-Benedicta_es_celorum-136-176"},
//			new String[]{"3591_008_fecit_potentiam_josquin", "Jos2004-Magnificat_Quarti_toni-Verse_6_Fecit_potentiam"},
//			new String[]{"5263_12_in_exitu_israel_de_egipto_desprez-1", "Jos1704-In_exitu_Israel_de_Egypto-1-143"},
//			new String[]{"5263_12_in_exitu_israel_de_egipto_desprez-2", "Jos1704-In_exitu_Israel_de_Egypto-144-280"},
//			// JEP (has imprecise triplet onset(s)) // hier
//			new String[]{"5263_12_in_exitu_israel_de_egipto_desprez-3", "Jos1704-In_exitu_Israel_de_Egypto-281-401"},
//			new String[]{"5256_05_inviolata_integra_desprez-1", "Jos2404-Inviolata_integra_et_casta_es-1-63"},
//			// JEP (has imprecise triplet onset(s))
//fck			new String[]{"5256_05_inviolata_integra_desprez-2", "Jos2404-Inviolata_integra_et_casta_es-64-105"},
//			// JEP (has imprecise triplet onset(s))
//			new String[]{"5256_05_inviolata_integra_desprez-3", "Jos2404-Inviolata_integra_et_casta_es-106-144"},
//			new String[]{"4465_33-34_memor_esto-1", "Jos1714-Memor_esto_verbi_tui-1-165"},
//			// JEP (has imprecise triplet onset(s)) // hier
//			new String[]{"4465_33-34_memor_esto-2", "Jos1714-Memor_esto_verbi_tui-166-325"},
//			new String[]{"932_milano_108_pater_noster_josquin-1", "Jos2009-Pater_noster-1-120"},
//			new String[]{"932_milano_108_pater_noster_josquin-2", "Jos2009-Pater_noster-121-198"},
//			new String[]{"5252_01_pater_noster_desprez-1", "Jos2009-Pater_noster-1-120"},
//			new String[]{"5252_01_pater_noster_desprez-2", "Jos2009-Pater_noster-121-198"},
//			new String[]{"3649_072_praeter_rerum_seriem_josquin_T", "Jos2411-Preter_rerum_seriem-1-87"},
//			new String[]{"5253_02_praeter_rerum_seriem_desprez-1", "Jos2411-Preter_rerum_seriem-1-87"},
//			new String[]{"5253_02_praeter_rerum_seriem_desprez-2", "Jos2411-Preter_rerum_seriem-88-185"},
//			new String[]{"5694_03_motet_praeter_rerum_seriem_josquin-1", "Jos2411-Preter_rerum_seriem-1-87"},
//			new String[]{"5694_03_motet_praeter_rerum_seriem_josquin-2", "Jos2411-Preter_rerum_seriem-88-185"},	
//			new String[]{"1274_12_qui_habitat_in_adjutorio-1", "Jos1807-Qui_habitat_in_adjutorio_altissimi-1-155"},
//			// JEP (has imprecise triplet onset(s))
//			new String[]{"1274_12_qui_habitat_in_adjutorio-2", "Jos1807-Qui_habitat_in_adjutorio_altissimi-156-282"},
//			new String[]{"5264_13_qui_habitat_in_adjutorio_desprez-1", "Jos1807-Qui_habitat_in_adjutorio_altissimi-1-155"},
//			// JEP (has imprecise triplet onset(s))
//			new String[]{"5264_13_qui_habitat_in_adjutorio_desprez-2", "Jos1807-Qui_habitat_in_adjutorio_altissimi-156-282"},
//			// TODO 0, 1
//			new String[]{"933_milano_109_stabat_mater_dolorosa_josquin", "Jos2509-Stabat_mater__Comme_femme-1-88"},
//			new String[]{"5255_04_stabat_mater_dolorosa_desprez-1", "Jos2509-Stabat_mater__Comme_femme-1-88"},
//			// JEP (has imprecise triplet onset(s))
//fck			new String[]{"5255_04_stabat_mater_dolorosa_desprez-2", "Jos2509-Stabat_mater__Comme_femme-89-180"},
			// c. Chansons
//			new String[]{"4400_45_ach_unfall_was", "Jos2829-Qui_belles_amours"}, // barring messed up due to correction in bar 2 
//			new String[]{"4481_49_ach_unfal_wes_zeigst_du_mich", "Jos2829-Qui_belles_amours"}, // barring messed up due to correction in bar 2
//			new String[] {"4406_51_adieu_mes_amours", "Jos2803-Adieu_mes_amours"},
//			new String[] {"4467_37_adieu_mes_amours", "Jos2803-Adieu_mes_amours"},
//			new String[] {"1025_adieu_mes_amours", "Jos2803-Adieu_mes_amours-anacrusis"},	
//			new String[] {"1030_coment_peult_avoir_joye", "Jos2807-Comment_peult_avoir_joye"},
//			new String[] {"1275_13_faulte_d_argent", "Jos2907-Faulte_dargent"},
//			new String[] {"3638_061_lauda_sion_gombert_T", "Jos2911-Je_ne_me_puis_tenir_daimer"},
//			new String[] {"5148_51_respice_in_me_deus._F#_lute_T", "Jos2911-Je_ne_me_puis_tenir_daimer"},	
//			new String[] {"5260_09_date_siceram_morentibus_sermisy", "Jos2911-Je_ne_me_puis_tenir_daimer"},
//			new String[] {"4438_07_la_plus_des_plus", "Jos2722-La_plus_des_plus"},
////			new String[] {"4443_12_la_bernardina", "Jos2721-La_Bernardina"},
////			new String[] {"1033_la_bernadina_solo_orig", "Jos2721-La_Bernardina"},	
//			new String[] {"5191_18_mille_regres", "Jos2825-Mille_regretz"},
//			new String[] {"4482_50_mille_regrets_P", "Jos2825-Mille_regretz"},
//			new String[] {"4469_39_plus_nulz_regrets_P", "Jos2828-Plus_nulz_regrets"},
//			new String[] {"922_milano_098_que_voulez_vous_dire_de_moi", "Jos2832-Si_jay_perdu"},

			// Byrd
//			new String[]{"byrd-ah_golden_hairs"},
//			new String[]{"byrd-an_aged_dame"},
//			new String[]{"byrd-as_caesar_wept"},
//			new String[]{"byrd-blame_i_confess"},
////			new String[]{"delight_is_dead"},
//			new String[]{"byrd-in_angels_weed"},
//			new String[]{"byrd-o_lord_bow"},
//			new String[]{"byrd-o_that_we"},
//			new String[]{"byrd-quis_me_statim"},
//			new String[]{"byrd-rejoyce_unto_the"},
//			new String[]{"byrd-sith_death"},
//			new String[]{"byrd-the_lord_is"},
//			new String[]{"byrd-the_man_is"},
//			new String[]{"byrd-while_phoebus"}
			
			// Adriaenssen
//			new String[]{"abran-tant_vous_allez"},
//			new String[]{"berchem-o_s_io"},
//			new String[]{"costeley-la_terre_les"},
//			new String[]{"ferabosco-io_mi_son"},
//			new String[]{"lasso-appariran_per_me"},
// 			new String[]{"lasso-avecque_vous"},
//			new String[]{"lasso-madonna_mia_pieta"},
//			new String[]{"lasso-poi_che_l"},
//			new String[]{"rore-anchor_che_col"},
	
			// Olja thesis
//			new String[]{"D-Mbs_Mus.ms._1512_03v", "D-Mbs_Mus.ms._1512_03v_vm_all"},
//			new String[]{"D-Mbs_Mus.ms._1512_04r", "D-Mbs_Mus.ms._1512_04r_vm_all"},
//			new String[]{"D-Mbs_Mus.ms._1512_07v-08r", "D-Mbs_Mus.ms._1512_07v-08r_vm_all"},
//			new String[]{"D-Mbs_Mus.ms._1512_08v", "D-Mbs_Mus.ms._1512_08v_vm_all"},
//			new String[]{"D-Mbs_Mus.ms._1512_09r", "D-Mbs_Mus.ms._1512_09r_vm_all"},
			//
//			new String[]{"D-Mbs_Mus.ms._1512_09v", "D-Mbs_Mus.ms._1512_09v_vm_all"},
//			new String[]{"D-Mbs_Mus.ms._1512_11r", "D-Mbs_Mus.ms._1512_11r_vm_all"},
//			new String[]{"D-Mbs_Mus.ms._1512_11v", "D-Mbs_Mus.ms._1512_11v_vm_all"},
//			new String[]{"D-Mbs_Mus.ms._1512_12r", "D-Mbs_Mus.ms._1512_12r_vm_all"},
//			new String[]{"D-Mbs_Mus.ms._1512_12v", "D-Mbs_Mus.ms._1512_12v_vm_all"},
			//
//			new String[]{"D-Mbs_Mus.ms._1512_17r", "D-Mbs_Mus.ms._1512_17r_vm_all"},
//			new String[]{"D-Mbs_Mus.ms._1512_18r", "D-Mbs_Mus.ms._1512_18r_vm_all"},
//			new String[]{"D-Mbs_Mus.ms._1512_20v", "D-Mbs_Mus.ms._1512_20v_vm_all"},
//			new String[]{"D-Mbs_Mus.ms._1512_21r", "D-Mbs_Mus.ms._1512_21r_vm_all"},
//			new String[]{"D-Mbs_Mus.ms._1512_22v-23r", "D-Mbs_Mus.ms._1512_22v-23r_vm_all"},
			//
//			new String[]{"D-Mbs_Mus.ms._1512_25v-26r", "D-Mbs_Mus.ms._1512_25v-26r_vm_all"},
//			new String[]{"D-Mbs_Mus.ms._1512_26v", "D-Mbs_Mus.ms._1512_26v_vm_all"},
//			new String[]{"D-Mbs_Mus.ms._1512_29r", "D-Mbs_Mus.ms._1512_29r_vm_all"},
//			new String[]{"D-Mbs_Mus.ms._1512_29v-30r", "D-Mbs_Mus.ms._1512_29v-30r_vm_all"},
//			new String[]{"D-Mbs_Mus.ms._1512_38v-39r", "D-Mbs_Mus.ms._1512_38v-39r_vm_all"},
			
			new String[]{"D-B_Mus.ms._40632_18v-19r", "D-B_Mus.ms._40632_18v-19r"},

			
			// OLD
//			new String[]{"D-Mbs_Mus.ms._1512_03r", "D-Mbs_Mus.ms._1512_03r_vm_all"},
//			new String[]{"D-Mbs_Mus.ms._1512_04v-05r", "D-Mbs_Mus.ms._1512_04v-05r_vm_6v"},
//			new String[]{"D-Mbs_Mus.ms._1512_09r", "D-Mbs_Mus.ms._1512_09r_vm_all"},
//			new String[]{"D-Mbs_Mus.ms._1512_17r", "D-Mbs_Mus.ms._1512_17r_vm_all"},
//			new String[]{"D-Mbs_Mus.ms._1512_21v-22r", "D-Mbs_Mus.ms._1512_21v-22r_vm_6v"},
//			new String[]{"D-Mbs_Mus.ms._1512_24v-25r", "D-Mbs_Mus.ms._1512_24v-25r_vm_all"}, // has NON-MAPPED SNU CASE
//			new String[]{"D-Mbs_Mus.ms._1512_39v-40r", "D-Mbs_Mus.ms._1512_39v-40r_vm_all"}

//			new String[]{"D-Mbs_Mus.ms._1512_03r", "D-Mbs_Mus.ms._1512_03r_vm_scholar"},
//			new String[]{"D-Mbs_Mus.ms._1512_04v-05r", "D-Mbs_Mus.ms._1512_04v-05r_vm_scholar"}, // has NON-MAPPED SNU CASE
//			new String[]{"D-Mbs_Mus.ms._1512_09r-ALT", "D-Mbs_Mus.ms._1512_09r_vm_scholar"},
//			new String[]{"D-Mbs_Mus.ms._1512_17r", "D-Mbs_Mus.ms._1512_17r_vm_scholar"},
//			new String[]{"D-Mbs_Mus.ms._1512_21v-22r", "D-Mbs_Mus.ms._1512_21v-22r_vm_scholar"}, // has NON-MAPPED SNU CASE
//			new String[]{"D-Mbs_Mus.ms._1512_24v-25r", "D-Mbs_Mus.ms._1512_24v-25r_vm_scholar"}, // has NON-MAPPED SNU CASE
			
			// Other
//			new String[] {"je_prens_en_gre-tab-rests", "je_prens_en_gre-SATB"},
		});
		return pieces;
	}


	private static List<String> getPiecesToSkip() {
		List<String> skip = Arrays.asList(new String[]{
			// orn-RIGHT
//			"3585_002_benedictus_de_missa_pange_lingua_josquin", // problem with unison in b. 35
///			"4965_01b_per_illud_ave_josquin", // problem with getMetricPosition
///			"5254_03_benedicta_es_coelorum_desprez-1", // problem with getMetricPosition
///			"5702_benedicta-1", // problem with getMetricPosition
///			"5252_01_pater_noster_desprez-2", // problem with getMetricPosition
//			// unorn-LEFT
//			"4471_40_cum_sancto_spiritu",
//			"5266_15_cum_sancto_spiritu_desprez",
//			"3643_066_credo_de_beata_virgine_jospuin_T-1",
//			"3643_066_credo_de_beata_virgine_jospuin_T-2",
//			"5106_10_misa_de_faysan_regres_2_gloria",
//			"5189_16_sanctus_and_hosanna_from_missa_faisant_regrets-1",
//			"5107_11_misa_de_faysan_regres_pleni",
//			"5189_16_sanctus_and_hosanna_from_missa_faisant_regrets-2",
//			"5188_15_sanctus_and_hosanna_from_missa_hercules-1",
//			"3584_001_pleni_missa_hercules_josquin",
//			"5188_15_sanctus_and_hosanna_from_missa_hercules-2",
//			"5190_17_cum_spiritu_sanctu_from_missa_sine_nomine",
		});
		return skip;
	}


	// O B S O L E T E  /  T O D O
	/**
	 * Gets, for each of the given available voices, the pitch of the last note before the
	 * given onset, and stores voice and pitch in the list returned.
	 * 
	 * @param availableVoices
	 * @param activeVoices
	 * @param mappedVoices
	 * @param pitchesTab
	 * @param voiceLabelsCurrChord
	 * @param trans
	 * @param currOnset
	 * @param numVoices
	 * 
	 * @return A list of voice-pitch pairs, organised in decending order (i.e., starting with the
	 *         lowest voice.) 
	 */
	private static List<Integer[]> getLastPitchInAvailableVoices(List<Integer> availableVoices,
		List<Integer> activeVoices, List<Integer> mappedVoices, List<Integer> pitchesTab,
		List<List<Double>> voiceLabelsCurrChord, Transcription trans, Rational currOnset, int numVoices
		) {
		
		// For available voices
		List<Integer[]> lastPitchInAvailableVoices = 
			getLastPitchInVoices(availableVoices, numVoices, 
			currOnset, trans);					

		// If not all available voices have been active: recalculate with active 
		// voices; complement with mapped (new) voices in current chord
		if (lastPitchInAvailableVoices.size() < availableVoices.size()) {	
			// Add active voices (list remains empty if current chord is the first)
			lastPitchInAvailableVoices = 
				getLastPitchInVoices(activeVoices, numVoices, 
				currOnset, trans);
			// Add any remaining mapped voices
			if (lastPitchInAvailableVoices.size() < mappedVoices.size()) {
				List<Integer> activeVoicesMapped = 
					ToolBox.getItemsAtIndex(lastPitchInAvailableVoices, 0);
				for (int j = 0; j < mappedVoices.size(); j++) {
					int mappedVoice = mappedVoices.get(j);
					// If mappedVoice is a new voice, i.e., one not in activeVoicesMapped
					if (!activeVoicesMapped.contains(mappedVoice)) {
						// Find the voice label in which mappedVoice is set to 
						// 1.0. The elements in pitchesTab correspond to those 
						// in voiceLabelsCurrChord
						int mappedPitch = -1;
						for (int k = 0; k < voiceLabelsCurrChord.size(); k++) {
							List<Double> curr = voiceLabelsCurrChord.get(k);
							if (curr != null && curr.get(mappedVoice) == 1.0) {
								mappedPitch = pitchesTab.get(k);
							}
						}
						lastPitchInAvailableVoices.add(new Integer[]{mappedVoice, mappedPitch});
					}
				}
				lastPitchInAvailableVoices = ToolBox.sortBy(lastPitchInAvailableVoices, 0);
				Collections.reverse(lastPitchInAvailableVoices);
			}
			// If the chord has no mapped voices (which happens when a note is 
			// part of a leading ornamental run)
//			else if (lastPitchInAvailableVoices.size() == 0 && mappedVoices.size() == 0) {
//				isLeadingFlourish = true;
//				// Determine the pitch the run leads into, and its voice in the MIDI
//				int firstClosestVoice = 0;
//				int pitchInFirstClosestVoice = trans.getPiece().getScore().
//				get(firstClosestVoice). // NotationStaff
//				get(0). // NotationVoice
//				get(0). // NotationChord
//				get(0). // Note
//				getMidiPitch();							
//				lastPitchInAvailableVoices.add(new Integer[]{firstClosestVoice, pitchInFirstClosestVoice});
//			}
		}
		return lastPitchInAvailableVoices;
	}


	private static List<Object> mapTabChordToMIDIOLD(List<Integer> pitchesTab, List<Integer> indicesTab, 
		List<Integer> pitchesGT, int numVoices) {
		List<Double> emptyVoiceLabel = makeEmptyVoiceLabel(numVoices);

		List<Integer> pitchesNotInMIDI = new ArrayList<>();
		List<Integer> indPitchesNotInMIDI = new ArrayList<>();
		List<Integer> mappedVoices = new ArrayList<>();
		List<Integer> activeVoices = new ArrayList<>();
		List<Integer> nonMappedSNUPitches = new ArrayList<>();
		List<Integer> extendedSNUVoices = new ArrayList<>();
		List<List<Double>> voiceLabelsChord = new ArrayList<>();

		for (int i = 0; i < pitchesTab.size(); i++) {
			int pitchInTab = pitchesTab.get(i);
			int pitchInd = indicesTab.get(i);

			List<Double> currVoiceLabel = new ArrayList<Double>(emptyVoiceLabel);

			// Map the pitch to (a) voice(s) and create the voice label
			// case		notes tab 	notes MIDI			
			// (a)		1			0		--> unmapped pitch
			// (b)		1			1		--> mapped pitch
			// (c)		1			2		--> mapped SNU
			// (d)		1			> 2		--> extended SNU		
			// (e)		2			0		--> unmapped unison (assumed to be rare)
			// (f)		2			1		--> half-mapped unison
			// (g)		2			2		--> mapped unison
			// (h)		2			> 2		--> extended unison (unison + SNU)
			// NB: In the case of (a) and (e) above, the for-loop below is skipped and 
			// the pitches are added to pitchesNotInMIDI
			for (int j = 0; j < pitchesGT.size(); j++) {
				if (pitchesGT.get(j) != null && pitchesGT.get(j) == pitchInTab) {
					int pitchInGT = pitchesGT.get(j);
					int voice = (numVoices-1) - j;
					int freqInTab = Collections.frequency(pitchesTab, pitchInTab); 
					int freqInGT = Collections.frequency(pitchesGT, pitchInTab);

					// Setting byrdAfterCorrectionHalfMapped to true makes initial results 
					// (as on Google doc) incorrect for 
					// IAW: M_a 17 --> 18
					// TLI: M_a  4 -->  5
					// TMI: M_a 18 --> 19
					// TODO remove boolean variable
					boolean byrdAfterCorrectionHalfMapped = true; 

					// (b) Mapped pitch (non-SNU and non-unison note)
					if (freqInTab == 1 && freqInGT == 1) {
						currVoiceLabel.set(voice, 1.0);
					}
					// (c) Mapped SNU
					else if (freqInTab == 1 && freqInGT == 2) {
						// If there is room for a SNU: set voice
						if (pitchesTab.size() < numVoices) {
							currVoiceLabel.set(voice, 1.0);
						}
						// If there is no room for a SNU 
						// NB This is assumed not to happen in an extended SNU case
						else {
							if (!byrdAfterCorrectionHalfMapped) {
								// Do not set voice (pitchInTab is added to pitchesNotInMIDI)
								nonMappedSNUPitches.add(pitchInTab);
							}
							else {
								// If pitchInTab has not been set to a voice (i.e., is the lower
								// SNU note, which comes first in pitchesInGT): set voice (this 
								// will be the lower SNU voice; after setting, there is no more
								// space to set the pitch also to the upper SNU voice)
								if (pitchesGT.indexOf(pitchInGT) == j) {
									currVoiceLabel.set(voice, 1.0);
								}
								// Else: do not set voice (pitchInTab is added to pitchesNotInMIDI)
								else {
									nonMappedSNUPitches.add(pitchInTab);
								}
							}
						}
					}
					// (d) Extended SNU (a single note assigned to more than two voices)
					// NB It is assumed that there will always be room for at least one SNU
					else if (freqInTab == 1 && freqInGT > 2) {
						currVoiceLabel.set(voice, 1.0);
					}
					// (f) Half-mapped unison
					else if (freqInTab == 2 && freqInGT == 1 && byrdAfterCorrectionHalfMapped) {
						// Add only if first unison note 
						if (!activeVoices.contains(voice)) {
							currVoiceLabel.set(voice, 1.0);
						}
					}
					// (g) Mapped unison 
					else if (freqInTab == 2 && freqInGT == 2) {
						// Add only if first and last indices in lists align
						if (j == pitchesGT.indexOf(pitchInTab) && i == pitchesTab.indexOf(pitchInTab) || 
							j == pitchesGT.lastIndexOf(pitchInTab) && i == pitchesTab.lastIndexOf(pitchInTab)) {
							currVoiceLabel.set(voice, 1.0);
						}
					}
					// (h) Extended unison (unison + SNU; the second unison note is a SNU)
					// NB: freqInTab can only be 2 in case of a unison
					// NB2: it is assumed that freqInGT == 3
					// Examples:
					// Inviolata, pt. 2, b. 76: f-f-f = 5256_05_...-2, b. 13: f-f
					// Preter, pt.2, b. 143: d-d-d-a  = 5253_02_...-2, b. 56: d-d-a
					//                                = 5694_03_...-2, b. 56: d-d-a
					// Preter, pt.2, b. 164: d-d-d-a  = 5694_03_...-2, b. 77: d-d-a
					// Je ne me puis, b. 54: a-a-a    = 5260_09      , b. 54: a-a-e
					else if (freqInTab == 2 && freqInGT > 2) {
						// Get the last index of the unison note, which is the second index 
						// in pitchesGT
						int secondInd = ToolBox.getIndexOfNthItem(pitchesGT, pitchInTab, 2);
						// First and second unison note: add only if first and second indices in lists align
						if (j == pitchesGT.indexOf(pitchInTab) && i == pitchesTab.indexOf(pitchInTab) || 
							j == secondInd && i == pitchesTab.lastIndexOf(pitchInTab)) {
							currVoiceLabel.set(voice, 1.0);
						}
						// Second unison note: add as SNU 
						if (i == pitchesTab.lastIndexOf(pitchInTab) && j == pitchesGT.lastIndexOf(pitchInTab)) {
							currVoiceLabel.set(voice, 1.0);
						}
					}

					// If the voice has been set: add to mapped voices
					if (currVoiceLabel.get(voice) != 0.0 && !mappedVoices.contains(voice)) {
						mappedVoices.add(voice);
					}
					// Add to active voices if not done yet
					if (currVoiceLabel.get(voice) != 0.0 && !activeVoices.contains(voice)) {
						activeVoices.add(voice);
					}	
				}
			}
			// Add the voice label to voiceLabelsCurrChord
			if (!currVoiceLabel.equals(emptyVoiceLabel)) {
				voiceLabelsChord.add(currVoiceLabel);
			}
			else {
				voiceLabelsChord.add(null);
				pitchesNotInMIDI.add(pitchInTab);
				indPitchesNotInMIDI.add(pitchInd);
			}
		}

		// If voiceLabelsChord contains an item with more than two 1s, it represents an 
		// extended SNU
		// NB It is assumed that there will only be one such item
		for (List<Double> l : voiceLabelsChord) {
			if (l != null && Collections.frequency(l, 1.0) > 2) {
				for (int j = 0; j < l.size(); j++) {
					if (l.get(j) == 1.0) {
						extendedSNUVoices.add(j);
					}
				}
			}
		}

		List<List<Integer>> intLists = new ArrayList<>();
		intLists.add(pitchesNotInMIDI);
		intLists.add(indPitchesNotInMIDI);
		intLists.add(mappedVoices);
		intLists.add(activeVoices);
		intLists.add(nonMappedSNUPitches);
		intLists.add(extendedSNUVoices);	
		return Arrays.asList(new Object[]{intLists, voiceLabelsChord});
	}


	/**
	 * Maps the pitches in the tablature chord that are not in the MIDI to a voice by finding the 
	 * closest last pitch (in semitones) in all voices. 
	 *
	 * @param pitchesTab
	 * @param pitchesGT
	 * @param pitchesNotInMIDI Returned in adapted form
	 * @param indPitchesNotInMIDI Returned in adapted form
	 * @param pitchesNotInMIDIOriginal Returned in adapted form
	 * @param nonMappedSNUPitches
	 * @param extendedSNUVoices
	 * @param mappedVoices
	 * @param voiceLabelsCurrChord Returned in adapted form
	 * @param keyInfo
	 * @param currOnset
	 * @param trans
	 * @param prevPitches Non-<code>null</code> when the chord is possibly a consecutive
	 *                    tuplet chord.
	 * @param prevVoiceLabels Non-<code>null</code> when the chord is possibly a consecutive
	 *                        tuplet chord.
	 * @return A list containing
	 * <ul>
	 * <li>As element 0: a list of lists, containing
	 * <ul>
	 *               <li>As element 0: the tab pitches not in the MIDI.</li>
	 *               <li>As element 1: the indices of the tab pitches not in the MIDI.</li>
	 *               <li>As element 2: the tab pitches not in the MIDI, in its original (complete) form.</li>
	 *               <li>As element 3: the indices of any pitches flagged as repetition.</li>
	 *               <li>As element 4: the indices of any pitches flagged as other.</li>
	 * </ul>
	 * </li>
	 * <li>As element 1: a list of voice labels the size of pitchesTab, containing the label for each 
	 *                   mapped pitch.</li>
	 * <li>As element 2: a list containing, for each tab pitch not in the MIDI
	 * <ul>
	 *               <li>As element 0: the voice it has been assigned to.</li>
	 *               <li>As element 1: the pitch.</li>
	 *               <li>As element 2: the cost at which it has been assigned to the voice.</li>
	 * </ul>
	 * </li>
	 * </ul>
	 */
	static List<Object> mapPitchesNotInMIDIOLD(List<Integer> pitchesTab, List<Integer> pitchesGT,
		List<Integer> pitchesNotInMIDI, List<Integer> indPitchesNotInMIDI,
		List<Integer> pitchesNotInMIDIOriginal, List<Integer> nonMappedSNUPitches,
		List<Integer> extendedSNUVoices, List<Integer> mappedVoices, List<List<Double>> 
		voiceLabelsCurrChord, List<Integer[]> keyInfo, Rational currOnset, Transcription trans,
		List<Integer> prevPitches, List<List<Double>> prevVoiceLabels){

		int mnv = Transcription.MAX_NUM_VOICES;
		int numVoices = trans.getNumberOfVoices();
		List<Integer> allVoices = 
			IntStream.rangeClosed(0, numVoices-1).boxed().collect(Collectors.toList());
		int numVoicesMappedOnto = 0;
		for (List<Double> l: voiceLabelsCurrChord) {
			if (l != null) {
				numVoicesMappedOnto += (Collections.frequency(l, 1.0));
			}
		}
		
		// Assume one key for the whole piece
		Integer[] key = keyInfo.get(0); 
		int keySig = key[Transcription.KI_KEY]; // num b (<0) / # (>0)
		int mode = key[Transcription.KI_MODE]; // major (0) / minor (1)
//		int base = PitchKeyTools.KEY_SIG_MPCS.get(keySig)[mode]; // LALALA
//		int base = MEIExport.KEY_SIG_MPCS.get(keySig)[scale]%12;
//		int base = MEIExport.KEY_SIGS.get(keySig)[scale]%12;
//		List<Integer[]> fictaPairs = (mode == 0) ? fictaPairsMajor : fictaPairsMinor;
//		List<Integer> intervals = (mode == 0) ? new ArrayList<Integer>(MAJOR) : new ArrayList<Integer>(MINOR);		
//		List<Integer> baseIntervals = new ArrayList<>();
//		intervals.forEach((interval) -> baseIntervals.add((interval + base)%12));
		
		List<Object> grids = PitchKeyTools.createGrids(keySig, mode); // LALALA
		Integer[] mpcGrid = (Integer[]) grids.get(0);
		String[] altGrid = (String[]) grids.get(1);
		String[] pcGrid = (String[]) grids.get(2);
		
		List<Integer> repetitionInds = new ArrayList<>();
//		List<Integer> fictaInds = new ArrayList<>();
		List<Integer> otherInds = new ArrayList<>();
		
		// Find the cheapest mapping of pitchesNotInMIDI. In three cases, two iterations
		// are needed, and in one special case three; otherwise only one:
		// iteration 0: find the cheapest combination of all availableVoices-sized subsets of 
		//    	        pitchesInMIDI and the available voices
		// iteration 1: find the cheapest combination of the remaining pitchesInMIDI and _all_ voices
		// iteration 2: repeat iteration 1 (e.g., 5702_benedicta-2, last chord)
		int iterations = 1; 
		if ((pitchesTab.size() > numVoices) || (extendedSNUVoices.size() != 0) || 
			(numVoicesMappedOnto >= numVoices)) {
			iterations = 2;
		}
		if (pitchesNotInMIDI.size() - numVoices - numVoices >= 1) {
			iterations = 3;
		}
		boolean isConsecutiveTupletChord = false;
		List<Integer[]> cheapestMappingTotal = new ArrayList<>();
		for (int iter = 0; iter < iterations; iter++) {
//-*-			System.out.println("iteration               " + iter);
			// 1. Iteration 0: remove all already mapped voices from availableVoices
			List<Integer> availableVoices = new ArrayList<>(allVoices);
			if (iter == 0) {  
				for (int v : mappedVoices) {
					availableVoices.remove(availableVoices.indexOf(v));
				}
				// If there are no availableVoices: jump to iteration 1
				if (availableVoices.isEmpty()) {
					continue;
				}
			}
//-*-			System.out.println("availableVoices         " + availableVoices);

			// 2. For each available voice, get the pitch of the last note before currOnset, 
			// and store voice and pitch together
			List<Integer[]> lastPitchInAvailableVoices = 
				getLastPitchInVoices(availableVoices, numVoices, currOnset, trans);
			List<Integer> activeAvailableVoices = 
				ToolBox.getItemsAtIndex(lastPitchInAvailableVoices, 0);

			// If there are more pitches not in MIDI than there are active available voices: one 
			// or more voices must be assigned multiple notes. Add voice-pitch pairs for all newly 
			// starting voices, i.e., all mapped voices in the chord that are not active voices,
			// to lastPitchInAvailableVoices
			if (pitchesNotInMIDI.size() > activeAvailableVoices.size() 
				&& Collections.frequency(pitchesNotInMIDI, null) != pitchesNotInMIDI.size() 
				){ // the part after the && is to prevent unnecessary iterations
				if (prevPitches != null && prevPitches.size() == pitchesTab.size()) {
					isConsecutiveTupletChord = true;
					System.out.println("isConsecutiveTupletChord in " + trans.getName());
				}

				for (int j = 0; j < mappedVoices.size(); j++) {
					int mappedVoice = mappedVoices.get(j);
					if (!activeAvailableVoices.contains(mappedVoice)) {
						// Find the voice label in which mappedVoice is set to 1.0 and then the 
						// pitch going with the voice. The elements in pitchesTab correspond to 
						// those in voiceLabelsCurrChord
						int mappedPitch = -1;
						for (int k = 0; k < voiceLabelsCurrChord.size(); k++) {
							List<Double> curr = voiceLabelsCurrChord.get(k);
							if (curr != null && curr.get(mappedVoice) == 1.0) {
								mappedPitch = pitchesTab.get(k);
							}
						}
						lastPitchInAvailableVoices.add(new Integer[]{mappedVoice, mappedPitch});
					}
				}
				lastPitchInAvailableVoices = ToolBox.sortBy(lastPitchInAvailableVoices, 0);
				Collections.reverse(lastPitchInAvailableVoices);
			}
//-*-			System.out.println("activeAvailableVoices   " + activeAvailableVoices);
//-*-			String lpiav =     "lastPitchInAvlblVoices  ";
//-*-			for (Integer[] in : lastPitchInAvailableVoices) {
//-*-				lpiav += Arrays.toString(in) + " ";
//-*-			}
//-*-			System.out.println(lpiav);

			// 3. Make subsets of pitchesNotInMIDI that are the size of availableVoices
			List<List<Integer>> subsetsOfPitchesNotInMIDI = new ArrayList<>();
			if (pitchesTab.size() > numVoices) {
				// Iteration 0
				if (pitchesNotInMIDI.size() >= availableVoices.size()) { 
					subsetsOfPitchesNotInMIDI = 
						ToolBox.getSubsets(pitchesNotInMIDI, availableVoices.size());
				}
				// Iteration 1-, when items haven been removed from pitchesNotInMIDI
				if (pitchesNotInMIDI.size() < availableVoices.size()) {
					subsetsOfPitchesNotInMIDI.add(pitchesNotInMIDI);
				}
			}
			else {
				subsetsOfPitchesNotInMIDI.add(pitchesNotInMIDI);
			}

			// Pad each element of subsetsOfPitchesNotInMIDI with nulls so that it has the size 
			// of lastPitchInAvailableVoices
			for (List<Integer> l : subsetsOfPitchesNotInMIDI) {
				int numNulls = lastPitchInAvailableVoices.size() - l.size();
				for (int j = 0; j < numNulls; j++) { 
					l.add(null);
				}
			}
//-*-			System.out.println("subsetsOfPtchsNotInMIDI " + subsetsOfPitchesNotInMIDI);

			// 4. For each subset of subsetsOfPitchesNotInMIDI: find the cheapest combination and
			// set cheapestMapping. comb contains all possible index combinations of 
			// lastPitchInAvailableVoices and a subset of pitchesNotInMIDI
			int cheapest = Integer.MAX_VALUE;
			List<Integer[]> cheapestMapping = null;
			List<List<Integer[]>> comb = ToolBox.getCombinations(lastPitchInAvailableVoices.size());
			
			for (int k = 0; k < subsetsOfPitchesNotInMIDI.size(); k++) {
				List<Integer[]> currCheapestMapping = 
					getCheapestMapping(subsetsOfPitchesNotInMIDI.get(k), comb, lastPitchInAvailableVoices);
				int currCheapest = 
					ToolBox.sumListInteger(ToolBox.getItemsAtIndex(currCheapestMapping, 2));
				if (currCheapest < cheapest) {
					cheapest = currCheapest;
					cheapestMapping = currCheapestMapping;
				}
			}
//			System.out.println("- - - - - - - - -");
//			cheapestMapping.forEach(in -> System.out.println(Arrays.asList(in)));
//			System.out.println("- - - - - - - - -");

			// In the case of a non-mapped SNU: if this pitch is mapped onto one of the two voices 
			// with the same pitch in the GT, it is not a mismatch and can be removed from cheapestMapping
			// NB In the case of two iterations, only the iteration in which the pitch is mapped 
			// must be checked; this is what the part after the && is for
			List<Integer> currMappedPitches = ToolBox.getItemsAtIndex(cheapestMapping, 1); 
			System.out.println(nonMappedSNUPitches);
			System.out.println(currMappedPitches);
//			System.exit(0);
			if (nonMappedSNUPitches.size() != 0 && 
				currMappedPitches.contains(nonMappedSNUPitches.get(0))) {
				int currNonMappedSNUPitch = nonMappedSNUPitches.get(0); // contains 1 pitch
				int ind = currMappedPitches.indexOf(currNonMappedSNUPitch);
				int currNonMappedSNUVoice = ToolBox.getItemsAtIndex(cheapestMapping, 0).get(ind);
				// If currNonMappedSNUPitch is mapped onto a voice that has the same pitch in the 
				// GT: not a mismatch; correct lists and voice label
				if (pitchesGT.get((numVoices-1)-currNonMappedSNUVoice) == currNonMappedSNUPitch) {
					cheapestMapping.remove(ind);
					// Since the chord has a SNU it cannot have a unison, and currNonMappedSNUPitch 
					// will occur only once in indPitchesNotInMIDI
					// NB pitchesNotInMIDI is padded with nulls; this does not affect indexing
					indPitchesNotInMIDI.remove(pitchesNotInMIDI.indexOf(currNonMappedSNUPitch));
					pitchesNotInMIDI.remove((Integer) currNonMappedSNUPitch);
					pitchesNotInMIDIOriginal = new ArrayList<Integer>(pitchesNotInMIDI);
					if (pitchesNotInMIDIOriginal.contains(null)) {
						pitchesNotInMIDIOriginal = pitchesNotInMIDIOriginal.subList(
							0, pitchesNotInMIDIOriginal.indexOf(null)); 
					}
					voiceLabelsCurrChord.set(
						pitchesTab.indexOf(currNonMappedSNUPitch), 
						LabelTools.convertIntoVoiceLabel(Arrays.asList(new Integer[]{currNonMappedSNUVoice}), mnv));
//-*-					System.out.println("pitchesNotInMIDI        " + pitchesNotInMIDI + " (removal of non-mapped SNU)");
//					System.out.println("pitchesNotInMIDIOrig    " + pitchesNotInMIDIOriginal + " (removal of non-mapped SNU)");
//-*-					System.out.println("indPitchesNotInMIDI     " + indPitchesNotInMIDI + " (removal of non-mapped SNU)");
//-*-					System.out.println("voiceLabelsCurrChord    " + voiceLabelsCurrChord + " (removal of non-mapped SNU)");
				}
			}
			// If the chord is a consecutive tuplet chord: adapt cheapestMapping
			if (isConsecutiveTupletChord) {
//-**-			System.out.println("pitchesTab = " + pitchesTab);				
//-**-				System.out.println("pitchesNotInMIDI = " + pitchesNotInMIDI);
//-**-				System.out.println("prevPitches = " + prevPitches);
//-**-				System.out.println("prevVoiceLabels = " + prevVoiceLabels);
				cheapestMapping.clear();
				for (Integer p : pitchesNotInMIDI) {
					if (p != null) {
						int voice = 
							LabelTools.convertIntoListOfVoices(
							prevVoiceLabels.get(prevPitches.indexOf(p))).get(0);
						// Calculate cost by comparing with the pitch that goes with the 
						// available voice in the MIDI
						int pToCompareWith = -1;
						for (Integer[] in : lastPitchInAvailableVoices) {
							if (in[0] == voice) {
								pToCompareWith = in[1];
								break;
							}
						}
						cheapestMapping.add(new Integer[]{
							voice, p, 
//							-1, // value used for ISMIR 2019 paper 
							Math.abs(p-pToCompareWith)}); 
					}
				}
			}
			cheapestMappingTotal.addAll(cheapestMapping);

			// 6. Add pitch indices to correct list
			for (Integer[] in : cheapestMapping) {
				int pitch = in[1];
				int cost = in[2];
				int pitchInd = indPitchesNotInMIDI.get(pitchesNotInMIDIOriginal.indexOf(pitch));
				// Check for repetition
				if (cost == 0) {
					repetitionInds.add(pitchInd);
				}
				// If not repetition: adaptation
				else {
//					boolean possibleSharpFicta = 
//						pitchesGT.contains(pitch-1) && fictaPairs.stream().anyMatch(a -> 
//						Arrays.equals(a, new Integer[]{(pitch-base)%12, ((pitch-1)-base)%12}));
//					boolean possibleFlatFicta = 
//						pitchesGT.contains(pitch+1) && fictaPairs.stream().anyMatch(a ->
//						Arrays.equals(a, new Integer[]{(pitch-base)%12, ((pitch+1)-base)%12}));
					
//					// Check for ficta
//					String pName = null;
//					String pNameOtherNote = null;
//					if (pitchesGT.contains(pitch+1) || pitchesGT.contains(pitch-1)) {
//						// If pitch and pitch +/- 1 have the same pname, ficta applies
//						String[] paPitch = (String[]) PitchKeyTools.spellPitch(
//							pitch, keySig, Arrays.asList(new Object[]{mpcGrid, altGrid, pcGrid}), null
//						).get(0);
//						pName = paPitch[0];
//
//						if (pitchesGT.contains(pitch + 1)) {
//							String[] paSemitoneAbove = (String[]) PitchKeyTools.spellPitch(
//								pitch+1, keySig, Arrays.asList(new Object[]{mpcGrid, altGrid, pcGrid}), null
//							).get(0);
//							pNameOtherNote = paSemitoneAbove[0];
//						}
//						else {
//							String[] paSemitoneBelow = (String[]) PitchKeyTools.spellPitch(
//								pitch-1, keySig, Arrays.asList(new Object[]{mpcGrid, altGrid, pcGrid}), null
//							).get(0);
//							pNameOtherNote = paSemitoneBelow[0];
//						}
//					}
//					if (pName != null && pName.equals(pNameOtherNote)) {
//						fictaInds.add(pitchInd);
//						currOnset.reduce();
//					}
					
					// If not ficta: adaptation
//					else {
					otherInds.add(pitchInd);
//					}
					
					
//					if	(
////						!baseIntervals.contains(pitch%12) &&
//						// sharp ficta
//						pitchesGT.contains(pitch-1) && fictaPairs.stream().anyMatch(a -> 
//						Arrays.equals(a, new Integer[]{(pitch-base)%12, ((pitch-1)-base)%12}))
//						||
//						// flat ficta
//						pitchesGT.contains(pitch+1) && fictaPairs.stream().anyMatch(a ->
//						Arrays.equals(a, new Integer[]{(pitch-base)%12, ((pitch+1)-base)%12}))) {
//
//						// If pitch and pitch +/- 1 have the same pName, ficta applies
//						String[] paPitch = (String[]) MEIExport.spellPitch(
//							pitch, -1, null, keySig, mpcGrid, altGrid, pcGrid, null
//						).get(0);
//						System.out.println("pitch in tab:");
//						System.out.println(paPitch[0]);
//						System.out.println(paPitch[1]);
//					
//						System.out.println("pitch in GT:");
//						String pNameOtherNote = ""; 
//						if (pitchesGT.contains(pitch + 1)) {
//							String[] paSemitoneAbove = (String[]) MEIExport.spellPitch(
//								pitch+1, -1, null, keySig, mpcGrid, altGrid, pcGrid, null
//							).get(0);
//							pNameOtherNote = paSemitoneAbove[0];
//							System.out.println(paSemitoneAbove[0]);
//							System.out.println(paSemitoneAbove[1]);
//						}
//						if (pitchesGT.contains(pitch - 1)) {
//							String[] paSemitoneBelow = (String[]) MEIExport.spellPitch(
//								pitch-1, -1, null, keySig, mpcGrid, altGrid, pcGrid, null
//							).get(0);
//							pNameOtherNote = paSemitoneBelow[0];
//							System.out.println(paSemitoneBelow[0]);
//							System.out.println(paSemitoneBelow[1]);
//						}
//					
//						currOnset.reduce();
//						System.out.println(currOnset);
//						System.out.println(pitchesGT);
//						System.out.println(pitch);
//						System.out.println(base);
//						System.out.println(keySig);
//						System.out.println(mode);
//						System.out.println("---");
//						System.out.println(Arrays.asList(new Integer[]{(pitch-base)%12, ((pitch-1)-base)%12}));
////						if (pitchesGT.equals(Arrays.asList(new Integer[]{null, 62, null, 65})))
////						if (pitch != 53 && pitch != 68 && pitch != 66 && pitch != 65 && pitch != 56)
////						System.exit(0);
//						
//						if (paPitch[0].equals(pNameOtherNote)) {
//							fictaInd.add(pitchInd);
//							isFicta = true;
//						}
//						System.out.println(isFicta);
////						System.exit(0);
//					}
//					// If not ficta
//					if (!isFicta) {
//						// Adaptation
//						otherInd.add(pitchInd);
//					}
				
				
////				// Adaptation
////				else {
////					otherInd.add(pitchInd);
////				}
//				}
				}
			}

			// 7. Replace null voice labels with voice labels for unmapped pitches.
			// voiceLabelsCurrChord is aligned with pitchesTab; the element to replace is 
			// determined by finding the index of the unmapped pitch in pitchesTab
			for (Integer[] in : cheapestMapping) {
				int pitch = in[1];
				int voice = in[0];
				List<Double> label = LabelTools.convertIntoVoiceLabel(
					Arrays.asList(new Integer[]{voice}), mnv
				);
				// If unmapped unison (half-mapped unison (see above) is assumed not to happen)						
				if (Collections.frequency(pitchesTab, pitch) == 2) {
					// If first unison note has already been added: add only last unison note
					if (voiceLabelsCurrChord.get(pitchesTab.indexOf(pitch)) != null) {
						voiceLabelsCurrChord.set(pitchesTab.lastIndexOf(pitch), label);
					}
					// If not: add first unison note
					else {
						voiceLabelsCurrChord.set(pitchesTab.indexOf(pitch), label);
					}
				}
				// If unmapped single note
				// NB: unmapped single notes could be SNUs, but are assumed to be not
				else {
					voiceLabelsCurrChord.set(pitchesTab.indexOf(pitch), label);
				}
				// Fix any extended SNU case
				// NB It is assumed that there will only be one item in extendedSNUVoices
				if (extendedSNUVoices.size() != 0) {
					// Find extendedSNU label
					for (List<Double> l : voiceLabelsCurrChord) {
						if (l != null) {
							if (Collections.frequency(l, 1.0) > 2) {
								l.set(voice, 0.0);
							}
						}
					}
				}
			}
//-*-			System.out.println("voiceLabelsCurrChord    " + voiceLabelsCurrChord);

			// 8. Remove pitches mapped to available voices from pitchesNotInMIDI
			for (Integer[] in : cheapestMapping ) {
				pitchesNotInMIDI.remove(pitchesNotInMIDI.indexOf(in[1]));
			}
//-*-			System.out.println("pitchesNotInMIDI        " + pitchesNotInMIDI);
			if (isConsecutiveTupletChord) {
				break;
			}
		}

		// If, because of SNUs, all voices had already been mapped onto before mapping any pitches
		// not in MIDI: remove voices from SNUs to which pitches not in MIDI now have been mapped
		// NB: it is assumed that SNUs and unisons do not occur simultaneously // TODO this actually happens
		if (numVoicesMappedOnto >= numVoices && pitchesNotInMIDIOriginal.size() > 0) {
			for (int p : pitchesNotInMIDIOriginal) {
				int voiceForP = LabelTools.convertIntoListOfVoices(
					voiceLabelsCurrChord.get(pitchesTab.indexOf(p))).get(0);
				for (List<Double> l : voiceLabelsCurrChord) {
					if (l.get(voiceForP) == 1.0 && Collections.frequency(l, 1.0) == 2) {
						l.set(voiceForP, 0.0);
					}
				}
			}
//-*-			System.out.println("voiceLabelsCurrChord    " + voiceLabelsCurrChord + "(removal of voices from SNUs)");
		}

		List<List<Integer>> intLists = new ArrayList<>();
		intLists.add(pitchesNotInMIDI);
		intLists.add(indPitchesNotInMIDI);
		intLists.add(pitchesNotInMIDIOriginal);
		intLists.add(repetitionInds);
//		intLists.add(fictaInds);
		intLists.add(otherInds);	
		return Arrays.asList(new Object[]{intLists, voiceLabelsCurrChord, cheapestMappingTotal});
	}


//	private static void fitDuration(File f, double diminution) {
//		f = new File("C:/Users/Reinier/Desktop/ISMIR-2019/test/tab/3618_041_benedictus_from_missa_de_l_homme_arme_morales_T" + Encoding.EXTENSION);
//		List<RhythmSymbol> allRs = Arrays.asList(new RhythmSymbol[]{
//			RhythmSymbol.semifusa,
//			RhythmSymbol.fusa,
//			RhythmSymbol.semiminim,
//			RhythmSymbol.minim,
//			RhythmSymbol.semibrevis,
//			RhythmSymbol.brevis,
//		});
//		List<Integer> allDurs = new ArrayList<>();
//		for (RhythmSymbol r : allRs) {
//			allDurs.add(r.getDuration());
//		}
////		.asList(new Integer[]{1, 2, 4, 8, 16, 32});
//		
//		String tbp = ToolBox.readTextFile(f);
//		StringBuilder res = new StringBuilder(tbp);
//		for (int i = 0; i < tbp.length(); i++) {
//			String currChar = "";
//			if (i+2 <= tbp.length()) {
//				currChar = tbp.substring(i, i+2);
//			}
//			RhythmSymbol rs = RhythmSymbol.getRhythmSymbol(currChar);
//			if (rs != null && (allDurs.contains(rs.getDuration()))) {
////				System.out.println(rs.getEncoding());
//				for (int j = 0; j < allRs.size(); j++) {
//					RhythmSymbol currRs = allRs.get(j);
//					if (rs.getEncoding().equals(currRs.getEncoding())) {
////						System.out.println(allRs.get(j-1).getEncoding());
//						char first, second;
//						if (diminution == 2) {
//							first = allRs.get(j-1).getEncoding().charAt(0);
//							second = allRs.get(j-1).getEncoding().charAt(1);
//						}
//						else {
//							first = allRs.get(j+1).getEncoding().charAt(0);
//							second = allRs.get(j+1).getEncoding().charAt(1);
//						}
//						res.setCharAt(i, first);
//						res.setCharAt(i+1, second);
//						break;
//					}
//				}
//			}
//		}
//		System.out.println("----------------");
//		System.out.println(res.toString());
//		
//		ToolBox.storeTextFile(res.toString(), 
//			new File("C:/Users/Reinier/Desktop/ISMIR-2019/test/tab/3618_041_benedictus_from_missa_de_l_homme_arme_morales_T-halved" + Encoding.EXTENSION));
//		System.exit(0);
//	}

}
