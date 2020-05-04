package tabmapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import analysis.Analyser;
import de.uos.fmt.musitech.data.score.NotationChord;
import de.uos.fmt.musitech.data.score.NotationSystem;
import de.uos.fmt.musitech.data.score.NotationVoice;
import de.uos.fmt.musitech.data.structure.Note;
import de.uos.fmt.musitech.data.structure.Piece;
import de.uos.fmt.musitech.utility.math.Rational;
import exports.MEIExport;
import exports.MIDIExport;
import representations.Tablature;
import representations.Transcription;
import tools.ToolBox;
import utility.DataConverter;

public class TabMapper {

	private static final int BAR_IND = 0;
	private static final int ONSET_IND = 1;
	public static final int SMALLEST_DUR = Tablature.SMALLEST_RHYTHMIC_VALUE.getDenom();
	private static final int NUM_COURSES = 6;
	private static enum Connection {LEFT, RIGHT};
	private static StringBuffer resultsOverAllPieces;
	private static List<String> shortNames;
//	private static double[][] resultsOverAllPiecesArr;
	private static String[][] resultsOverAllPiecesArrStr;
	private static Integer[] intsToAvg;
	private static Double[] doublesToAvg;

	private static int totalNumNotes = 0;

	private static final Map<Integer, Integer[] > KEY_SIGS;
	static { KEY_SIGS = new LinkedHashMap<Integer, Integer[]>();
		KEY_SIGS.put(4, new Integer[]{4, 1});   // E/c#
		KEY_SIGS.put(3, new Integer[]{9, 6});   // A/f#
		KEY_SIGS.put(2, new Integer[]{2, 11});  // D/b
		KEY_SIGS.put(1, new Integer[]{7, 4});   // G/e
		KEY_SIGS.put(0, new Integer[]{0, 9});   // C/a
		KEY_SIGS.put(-1, new Integer[]{5, 2});  // F/d
		KEY_SIGS.put(-2, new Integer[]{10, 7}); // Bb/g
		KEY_SIGS.put(-3, new Integer[]{3, 0});  // Eb/c
		KEY_SIGS.put(-4, new Integer[]{8, 5});  // Ab/f
	}

	private static final List<Integer> MAJOR = Arrays.asList(new Integer[]{0, 2, 4, 5, 7, 9, 11});
	private static final List<Integer> MINOR = Arrays.asList(new Integer[]{0, 2, 3, 5, 7, 8, 10});

	// F major example
	// F     G     A  Bb    C     D     E 
	//    F#    Ab       B     C#    Eb
	// 65 66 67 68 69 70 71 72 73 74 75 76 
	// 0  1  2  3  4  5  6  7  8  9  10 11
	//
	// #1: 66 for 65 = 1  for 0
	// b2: 66 for 67 = 1  for 2
	// #2: 68 for 67 = 3  for 2
	// b3: 68 for 69 = 3  for 4
	// #4: 71 for 70 = 6  for 5
	// #5: 73 for 72 = 8  for 7
	// b6: 73 for 74 = 8  for 9 
	// #6: 75 for 74 = 10 for 9
	// b7: 75 for 76 = 10 for 11
	//
	// Each pair represents the ficta pitch (element 0) and the original pitch (element 1)
	private static List<Integer[]> fictaPairsMajor = Arrays.asList(new Integer[][]{
		// First note of pair is ficta (i.e., tab has ficta)
		new Integer[]{1, 0}, // #1
		new Integer[]{1, 2}, // b2
		new Integer[]{3, 2}, // #2
		new Integer[]{3, 4}, // b3
		new Integer[]{6, 5}, // #4
		new Integer[]{8, 7}, // #5
		new Integer[]{8, 9}, // b6
		new Integer[]{10, 9}, // #6
		new Integer[]{10, 11}, // b7
		// Second note of pair is ficta (i.e., GT has ficta)
		new Integer[]{0, 1}, // #1
		new Integer[]{2, 1}, // b2
		new Integer[]{2, 3}, // #2
		new Integer[]{4, 3}, // b3
		new Integer[]{5, 6}, // #4
		new Integer[]{7, 8}, // #5
		new Integer[]{9, 8}, // b6
		new Integer[]{9, 10}, // #6
		new Integer[]{11, 10} // b7
	});

	// D minor example
	// D     E  F     G     A  Bb    C  
	//    Eb       F#    Ab       B     C#
	// 62 63 64 65 66 67 68 69 70 71 72 73
	// 0  1  2  3  4  5  6  7  8  9  10 11
	// 
	// #1: 63 for 62 = 1 for 0
	// b2: 63 for 64 = 1 for 2
	// #3: 66 for 65 = 4 for 3
	// b4: 66 for 67 = 4 for 5
	// #4: 68 for 67 = 6 for 5
	// b5: 68 for 69 = 6 for 7
	// #6: 71 for 70 = 9 for 8
	// #7: 73 for 72 = 11 for 10
	// b1: 73 for 74 = 11 for 12
	//
	// Each pair represents the ficta pitch (element 0) and the original pitch (element 1)
	private static List<Integer[]> fictaPairsMinor = Arrays.asList(new Integer[][]{
		// First note of pair is ficta (i.e., tab has ficta)
		new Integer[]{1, 0}, // #1
		new Integer[]{1, 2}, // b2
		new Integer[]{4, 3}, // #3 
		new Integer[]{4, 5}, // b4
		new Integer[]{6, 5}, // #4
		new Integer[]{6, 7}, // b5
		new Integer[]{9, 8}, // #6
		new Integer[]{11, 10}, // #7
		new Integer[]{11, 0}, // b1
		// Second note of pair is ficta (i.e., GT has ficta)
		new Integer[]{0, 1}, // #1
		new Integer[]{2, 1}, // b2
		new Integer[]{3, 4}, // #3 
		new Integer[]{5, 4}, // b4
		new Integer[]{5, 6}, // #4
		new Integer[]{7, 6}, // b5
		new Integer[]{8, 9}, // #6
		new Integer[]{10, 11}, // #7
		new Integer[]{0, 11}, // b1
	});


	static List<Integer> models = new ArrayList<>();
	static List<Integer> modelsBnp = new ArrayList<>();
	public static void main(String[] args) {
		String path = "F:/research/projects/byrd/";
//		path = "C:/Users/Reinier/Desktop/2019-ISMIR/test/";
//		path = "C:/Users/Reinier/Desktop/IMS-tours/example/";
//		path = "C:/Users/Reinier/Desktop/2019-ISMIR/poster/imgs/";
		path = "F:/research/publications/conferences-workshops/2019-ISMIR/paper/josquintab/";

		boolean includeOrn = true;
		Connection connection = Connection.RIGHT;
		boolean grandStaff = false;
		boolean addDuration = false;
		List<String[]> pieces = getPieces();
		List<String> skip = getPiecesToSkip();

		resultsOverAllPieces = new StringBuffer();
		List<String> cols = Arrays.asList(new String[]{
			"piece", "N_model", "N_intab", 
			"M", "M_o", "M_r", "M_f", "M_a",
			"m", "m_oa", "m_a"
		});
		for (int i = 0; i < cols.size(); i++) {
			String s = cols.get(i);
			resultsOverAllPieces = (i < cols.size()-1) ? resultsOverAllPieces.append(s + "\t") :
				resultsOverAllPieces.append(s + "\r\n");
		}
		resultsOverAllPiecesArrStr = new String[pieces.size()+1][cols.size()];
		
		int numCols = cols.size();
		List<Integer> doubleInds = 
			IntStream.rangeClosed(numCols-3, numCols-1).boxed().collect(Collectors.toList());
		List<Integer> colsToSkip = Arrays.asList(new Integer[]{0});
		List<Object> listsToAvg = Analyser.getListsToAvg(numCols, doubleInds, colsToSkip);
		intsToAvg = (Integer[]) listsToAvg.get(0);
		doublesToAvg = (Double[]) listsToAvg.get(1);

		if (args.length > 0) {
			// Path
			path = args[0];
			if (!path.endsWith("/")) {
				path += "/";
			}
			MEIExport.setRootDir(path);
			MEIExport.MEITemplatePath = path;
			// Pieces
			pieces = new ArrayList<>();
			for (String line : ToolBox.readTextFile(new File(path + args[1])).split("\r\n")) {
				String[] split = line.split(",");
				pieces.add(new String[]{split[0].strip(), split[1].strip()});
			}
			// Ornamentation
//-**-			if (args.length == 3) {
//-**-				String opt = args[2]; 
//-**-				if (!opt.equals("-n")) {
//-**-					System.out.println("Unknown optional argument: " + opt);
//-**-				}
//-**-				else {
//-**-					includeOrn = false;
//-**-				}
//-**-			}
			// Options: add ornamentation, add duration, grand staff (each Y/N)
			if (args.length > 2) {
				String opt = args[3];
				String orn = opt.substring(0, 1);
				String dur = opt.substring(1, 2);
				String gs = opt.substring(2, 3);
				includeOrn = (orn.contains("y") || orn.contains("Y")) ? true : false;
				addDuration = (dur.contains("y") || dur.contains("Y")) ? true : false;
				grandStaff = (gs.contains("y") || gs.contains("Y")) ? true : false;
			}
		}

		// Map each piece in pieces
		shortNames = new ArrayList<>();
		for (int i = 0; i < pieces.size(); i++) {
			String[] piece = pieces.get(i);
			String tabName = piece[0];
			String modelName = piece[1];
			System.out.println("\r\n... mapping " + tabName + " ...");
			shortNames.add(ToolBox.getShortName(tabName));
	//		if (i == 0) {
	//			shortNames.set(0, "OSP"); //-**-
	//		}

			// Make tab; make model transcription
			Tablature tab = new Tablature(new File(path + "tab/" + tabName + ".tbp"), false);	
			Transcription model = 
				new Transcription(new File(path + "MIDI/" + modelName + ".mid"), null);
			models.add(model.getNumberOfNotes());
			modelsBnp.add(model.getBasicNoteProperties().length);
			// If necessary: adapt maximum number of voices 
			if (model.getNumberOfVoices() == 6) {
				Transcription.setMaximumNumberOfVoices(6);
			}
			if (Transcription.MAXIMUM_NUMBER_OF_VOICES == 6 && model.getNumberOfVoices() < 6) {
				Transcription.setMaximumNumberOfVoices(5);
			}

			Integer[][] btp = tab.getBasicTabSymbolProperties();
			Integer[][] bnp = model.getBasicNoteProperties();
			System.out.println("    tab has " + (btp[btp.length-1][Tablature.CHORD_SEQ_NUM] + 1) +
				" chords and " + btp.length + " notes");
			System.out.println("    MIDI has " + (bnp[bnp.length-1][Transcription.CHORD_SEQ_NUM] + 1) 
				+ " chords and " + bnp.length + " notes");
			System.out.println();
			totalNumNotes += btp.length;

			// Map tab onto model
			List<Object> mapping = map(model, tab, includeOrn, connection, i);
			List<List<Double>> voiceLabels = (List<List<Double>>) mapping.get(0);
			List<List<Integer>> mismatchInds = (List<List<Integer>>) mapping.get(1);
			String mappingDetails = (String) mapping.get(2);
			String mappingDetailsCSV = (String) mapping.get(3);

			// Remove all null voice labels from voiceLabels; remove the corresponding rows from 
			// btp. null voice labels are given to ornamental notes when includeOrn == false 
			if (!includeOrn) {
				List<List<Double>> voiceLabelsCopy = new ArrayList<>();
				Integer[][] btpCopy = 
					new Integer[voiceLabels.size() - Collections.frequency(voiceLabels, null)][btp[0].length];
				int ind = 0;
				for (int j = 0; j < voiceLabels.size(); j++) {
					if (voiceLabels.get(j) != null) {
						voiceLabelsCopy.add(voiceLabels.get(j));
						btpCopy[ind] = btp[j];
						ind++;
					}
				}
				voiceLabels = voiceLabelsCopy;
				btp = btpCopy;
			}

			// Store the results of the mapping process
			// a. As .txt and .csv, containing the mapping statistics
			ToolBox.storeTextFile(mappingDetails, new File(path + "mapped/" + tabName + "-mapping_details.txt"));
			ToolBox.storeTextFile(mappingDetailsCSV, new File(path + "mapped/" + tabName + "-mapping_details.csv"));
			// b. As MIDI (used to create a GT transcription for training a model) and MEI
			// (used to visualise the mismatches)
			Piece p = Transcription.createPiece(
				btp, null, voiceLabels, null, model.getNumberOfVoices(), 
				model.getPiece().getMetricalTimeLine(), model.getPiece().getHarmonyTrack());
			List<Integer> instruments = Arrays.asList(new Integer[]{MIDIExport.GUITAR});
			// Without full durations
			if (!addDuration) {
				// MIDI 
				File f = new File(path + "mapped/" + tabName + ".mid");
				MIDIExport.exportMidiFile(p, instruments, f.getAbsolutePath());
				// MEI 
				Transcription trans = new Transcription(f, null);
				trans.setColourIndices(mismatchInds);
				List<Integer[]> mi = (tab == null) ? trans.getMeterInfo() : tab.getMeterInfo();
				if (!skip.contains(tabName)) {
					MEIExport.exportMEIFile(trans, /*tab,*/ btp, mi, trans.getKeyInfo(), 
						tab.getTripletOnsetPairs(), mismatchInds, grandStaff, 
						path + "mapped/" + tabName);
				}
			}
			// With full durations 
			if (addDuration) {
				Rational maxDur = tab.getDiminutions().get(0) == 2 ? Rational.HALF : Rational.ONE; // TODO account for multiple dims per piece and for other values than 1 and 2 
				p = Transcription.completeDurations(p, maxDur);
				// MIDI
				File fDur = new File(path + "mapped/"+ tabName + "-dur.mid");
				MIDIExport.exportMidiFile(p, instruments, fDur.getAbsolutePath());
				// MEI
				Transcription transDur = new Transcription(fDur, null);
				transDur.setColourIndices(mismatchInds);
				List<Integer[]> mi = (tab == null) ? transDur.getMeterInfo() : tab.getMeterInfo();
				if (!skip.contains(tabName)) {
					MEIExport.exportMEIFile(transDur, /*tab,*/ btp, mi, transDur.getKeyInfo(), 
						tab.getTripletOnsetPairs(), mismatchInds, grandStaff, 
						path + "mapped/" + tabName + "-dur");
				}
			}
		}
//		System.out.println(models);
//		System.out.println(modelsBnp);

		System.out.println(
			pieces.size() + (pieces.size() == 1 ? " piece (" : " pieces (") + 
			totalNumNotes + " notes) processed" + "\r\n");
		
		System.out.println(resultsOverAllPieces);

		String latexTable = ToolBox.createLaTeXTable(resultsOverAllPiecesArrStr, intsToAvg,
			doublesToAvg, 0, 5, true);
		System.out.println(latexTable);
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
			System.out.println(diff);
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
	 * @return A list of voice labels. 
	 */
	private static List<Object> map(Transcription trans, Tablature tab, boolean includeOrnamentation,
		Connection connection, int pieceIndex) {
//-*-		System.out.println(">>> TabMapper.map() called");
		Integer[][] btp = tab.getBasicTabSymbolProperties();
		int numVoices = trans.getNumberOfVoices();
		List<Double> emptyVoiceLabel = makeEmptyVoiceLabel(numVoices);
		
		// Get key information. Assume one key for the whole piece
		List<Integer[]> keyInfo = 
			Transcription.createKeyInfo(trans.getPiece(), Transcription.createMeterInfo(trans.getPiece()));
//-*-		System.out.println("keyInfo");
//-*-		keyInfo.forEach(in -> System.out.println(Arrays.toString(in)));

		// Get meter information
		List<Integer[]> meterInfo = tab.getMeterInfo();
//-*-		System.out.println("meterInfo");
//-*-		meterInfo.forEach(in -> System.out.println(Arrays.toString(in)));

		// Set ornamentation threshold to the note value two levels below beat-level 
		// 3/1, 2/1: beat level is W; two levels below is Q
		// 3/2, 2/2: beat level is H; two levels below is E
		int ornThreshold = -1;
		if (meterInfo.get(0)[1] == 2) {
			ornThreshold = (Transcription.EIGHTH.indexOf(1.0) + 1)*3; // *3 trp dur
		}
		else if (meterInfo.get(0)[1] == 1) {
			ornThreshold = (Transcription.QUARTER.indexOf(1.0) + 1)*3; // *3 trp dur
		}
		else if (meterInfo.get(0)[0] == 4 && meterInfo.get(0)[1] == 4) { // TODO
			ornThreshold = (Transcription.EIGHTH.indexOf(1.0) + 1)*3; // * 3 trp dur
		}

		List<Integer[][]> gridAndMask = makeGridAndMask(trans, tab);
		Integer[][] grid = gridAndMask.get(0);
		Integer[][] mask = gridAndMask.get(1);
		for (Integer[] in : grid) {
			System.out.println(Arrays.toString(in));
		}
		System.out.println("-*-*-*-*-*-*-");
		for (Integer[] in : mask) {
			System.out.println(Arrays.toString(in));
		}
		List<List<Double>> voiceLabels = new ArrayList<List<Double>>();
		StringBuffer res = new StringBuffer();
		StringBuffer resCsv = new StringBuffer();
		List<String> csv = new ArrayList<>();
		int numMismatches = 0;
		List<Integer> ornamentationInd = new ArrayList<>();
		List<Integer> repetitionInd = new ArrayList<>();
		List<Integer> fictaInd = new ArrayList<>();
		List<Integer> otherInd = new ArrayList<>();
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
//			System.out.println("chord = " + i);
			Integer[] currGrid = grid[i];
			Integer[] currMask = mask[i];

			// Only if the tablature has a note at this onset time
			if (currMask[ONSET_IND + 1] != null) {
				Rational currOnset = new Rational(currGrid[ONSET_IND], SMALLEST_DUR);
				int currDur = currMask[(ONSET_IND + 1) + NUM_COURSES];
				boolean onsetInGT = Collections.frequency(Arrays.asList(Arrays.copyOfRange(
					currGrid, 2, currGrid.length)), null) != currGrid.length - 2;
				int chordInd = btp[currMask[(ONSET_IND + 1) + 2*NUM_COURSES]][Tablature.CHORD_SEQ_NUM];
//				String bar = 
//					currMask[0] + " " + Tablature.getMetricPosition(new Rational(currMask[1], 
//					SMALLEST_DUR), meterInfo)[1];
				String bar = 
					ToolBox.getMetricPositionAsString(Tablature.getMetricPosition(
					new Rational(currMask[ONSET_IND], SMALLEST_DUR), meterInfo));

				// Get pitches, arranged per voice (low-high), from GT
				List<Integer> pitchesGT = 
					Arrays.asList(Arrays.copyOfRange(currGrid, ONSET_IND+1, ONSET_IND+1 + numVoices));
				// Get pitches and indices, arranged low-high, from tablature
				List<Integer> pitchesTab = 
					Arrays.asList(Arrays.copyOfRange(currMask, ONSET_IND+1, ONSET_IND+1 + NUM_COURSES));
				List<Integer> indicesTab = 
					Arrays.asList(Arrays.copyOfRange(currMask, ONSET_IND+1 + 2*NUM_COURSES, ONSET_IND+1 + 3*NUM_COURSES));
				// Excluding any trailing null values
				if (pitchesTab.contains(null)) {
					pitchesTab = pitchesTab.subList(0, pitchesTab.indexOf(null));
					indicesTab = indicesTab.subList(0, indicesTab.indexOf(null));
				}

				// A chord is ornamental if
				// a. it is a single onset in the tablature AND
				// b. its duration is less than or equal to the ornamentation threshold AND
				// c. there is no note at the current onset time in the MIDI
				boolean isOrnamental = 
					(pitchesTab.size() == 1) && (currDur <= ornThreshold) && !onsetInGT;
//-*-				System.out.println("CHORD AT INDEX " + i);
//-*-				System.out.println("--- specifics for this chord ---");
//-*-				System.out.println("currGrid                " + Arrays.toString(currGrid));
//-*-				System.out.println("currMask                " + Arrays.toString(currMask));
//-*-				System.out.println("pitchesGT               " + pitchesGT);
//-*-				System.out.println("pitchesTab              " + pitchesTab);
//-*-				System.out.println("indicesTab              " + indicesTab);
//-*-				System.out.println("currDur                 " + currDur);
//-*-				System.out.println("currOrn                 " + currOrn);
				
				// If the chord is ornamental: add note index to running list and skip iteration
				if (isOrnamental) {
					currOrn.add(indicesTab.get(0));
					voiceLabels.add(null);
				}
				// If the chord is non-ornamental: map
				else {
//-*-					System.out.println("--- mapping tab to MIDI ---");
					// 1. Map pitches in the tab to pitches in the MIDI
					List<Object> initialMapping = 
						mapTabToMIDI(pitchesTab, indicesTab, pitchesGT, numVoices);
					List<List<Integer>> intLists = (List<List<Integer>>) initialMapping.get(0);
					List<Integer> pitchesNotInMIDI = intLists.get(0);
					List<Integer> pitchesNotInMIDIOriginal = new ArrayList<Integer>(pitchesNotInMIDI);
					List<Integer> indPitchesNotInMIDI = intLists.get(1);
					List<Integer> mappedVoices = intLists.get(2);
					List<Integer> currActiveVoices = intLists.get(3);
					for (int v : currActiveVoices) {
						if (!activeVoices.contains(v)) {
							activeVoices.add(v);
						}
					}
					Collections.sort(activeVoices);
					Collections.reverse(activeVoices);				
					List<Integer> nonMappedSNUPitches = intLists.get(4);
					List<Integer> extendedSNUVoices = intLists.get(5);
					List<List<Double>> voiceLabelsCurrChord = (List<List<Double>>) initialMapping.get(1);

//-*-					System.out.println("pitchesNotInMIDI        " + pitchesNotInMIDI);
//					System.out.println("pitchesNotInMIDIOrig    " + pitchesNotInMIDIOriginal);
//-*-					System.out.println("indPitchesNotInMIDI     " + indPitchesNotInMIDI);
//-*-					System.out.println("mappedVoices            " + mappedVoices);
//-*-					System.out.println("activeVoices            " + activeVoices);
//-*-					System.out.println("nonMappedSNUPitches     " + nonMappedSNUPitches);
//-*-					System.out.println("extendedSNUVoices       " + extendedSNUVoices);
//-*-					System.out.println("voiceLabelsCurrChord    " + voiceLabelsCurrChord);

					// 2. Map any pitches that are in the tab but not in the MIDI
					List<Integer[]> cheapestMappingTotal = null;
					if (voiceLabelsCurrChord.contains(null)) {
//-*-						System.out.println("--- mapping pitches not in MIDI ---");

						// In case of possible consecutive tupletChord
						List<Integer> prevPitches = null;
						List<List<Double>> prevVoiceLabels = null;
						if (lastNonOrnChordContainsTuplet && 
							Arrays.asList(new Rational[]{new Rational(1, 1), new Rational(1, 2), 
							new Rational(1, 4)}).contains(currOnset.sub(onsetLastOrnChord))) { // TODO
							prevPitches = pitchesTabLastNonOrnChordWithTuplet;
							prevVoiceLabels = voiceLabelsLastNonOrnChordWithTuplet;
						}
						List<Object> completedMapping = 
							mapPitchesNotInMIDI(pitchesTab, pitchesGT, pitchesNotInMIDI, 
							indPitchesNotInMIDI, pitchesNotInMIDIOriginal, nonMappedSNUPitches, 
							extendedSNUVoices, mappedVoices, voiceLabelsCurrChord, keyInfo, 
							currOnset, trans, prevPitches, prevVoiceLabels);
						List<List<Integer>> newIntLists = (List<List<Integer>>) completedMapping.get(0);
						pitchesNotInMIDI = newIntLists.get(0);
						indPitchesNotInMIDI = newIntLists.get(1);
						pitchesNotInMIDIOriginal = newIntLists.get(2);
						repetitionInd.addAll(newIntLists.get(3));
						fictaInd.addAll(newIntLists.get(4));
						otherInd.addAll(newIntLists.get(5));
						voiceLabelsCurrChord = (List<List<Double>>) completedMapping.get(1);
						cheapestMappingTotal = (List<Integer[]>) completedMapping.get(2);
						numMismatches += cheapestMappingTotal.size();

						res.append("no match for pitches " + pitchesNotInMIDIOriginal + " at indices " + 
							indPitchesNotInMIDI + " (bar " + currMask[0] + "; onset " + 
							Tablature.getMetricPosition(new Rational(currMask[ONSET_IND], SMALLEST_DUR), 
							meterInfo)[1] + ")" + "\r\n");
						res.append("pitches in tab chord : " + pitchesTab + "\r\n");
						res.append("cheapest mapping (total cost " + 
							ToolBox.sumListInteger(ToolBox.getItemsAtIndex(cheapestMappingTotal, 2))+ 
							"):"  + "\r\n");
						for (int j = 0; j < cheapestMappingTotal.size(); j++) {
							String curr = "";
							Integer[] in = cheapestMappingTotal.get(j);
							int p = in[1];
							int indexOfPitch = indPitchesNotInMIDI.get(pitchesNotInMIDIOriginal.indexOf(p));

							// In case of second unison pitch
							if (Collections.frequency(pitchesNotInMIDIOriginal, p) == 2 &&
								ToolBox.getItemsAtIndex(cheapestMappingTotal, 1).lastIndexOf(p) == j) {
								indexOfPitch = indPitchesNotInMIDI.get(pitchesNotInMIDIOriginal.lastIndexOf(p));
							}
							
							curr += indexOfPitch + "," + p + "," + chordInd + "," + bar + "," +
								in[0] + "," + in[2] + ",";
							res.append("  voice " + in[0] + " for pitch " + p + " (at cost " + in[2] + ")");
							if (repetitionInd.contains(indexOfPitch)) {
								res.append(" --> repetition");
								curr += "repetition";
							}
							else if(fictaInd.contains(indexOfPitch)) {
								res.append(" --> ficta");
								curr += "ficta";
							}
							else if(otherInd.contains(indexOfPitch)) {
								res.append(" --> other");
								curr += "other";
							}
							res.append("\r\n");
							csv.add(curr);
						}
						List<List<Integer>> voices = new ArrayList<>();
						voiceLabelsCurrChord.forEach(l -> 
							voices.add(DataConverter.convertIntoListOfVoices(l)));
						voices.forEach(l -> Collections.reverse(l));
						res.append("voices for chord: " + voices + "\r\n");
						res.append("\r\n");
					}
					voiceLabels.addAll(voiceLabelsCurrChord);

					// 3. Map any preceding ornamental notes still unassigned
					if (!currOrn.isEmpty()) {						
						// Add the ornamental notes to the voice going with the closest pitch
						if (includeOrnamentation) {
//-*-							System.out.println("--- mapping preceding ornamental notes ---");
							List<Integer> ornPitches = new ArrayList<>();
							currOrn.forEach(ind -> ornPitches.add(btp[ind][Tablature.PITCH]));
							int closestVoice = 
								mapPrecedingOrnamentalNotes(ornPitches, pitchesTab, voiceLabelsCurrChord,
								i, indLastNonOrnChord, pitchesTabLastNonOrnChord, voiceLabelsLastNonOrnChord,
								connection);

							List<Double> vl = new ArrayList<Double>(emptyVoiceLabel);
							vl.set(closestVoice, 1.0);

							// Replace the voiceLabels at the indices in currOrn
							for (int ind : currOrn) {
								ornamentationInd.add(ind);
								voiceLabels.set(ind, vl);
							}
//-*-							System.out.println("ornPitches              " + ornPitches );
//-*-							System.out.println("closestVoice            " + closestVoice);
							
							numMismatches += currOrn.size();
							res.append("pitches " + ornPitches + " at indices " + currOrn + 
								" flagged as ornamentation" + "\r\n");
							res.append("voice for ornamental note(s): " + closestVoice + "\r\n");
							res.append("\r\n");

							for (int j = 0; j < currOrn.size(); j++) {
								int ind = currOrn.get(j);
								Integer[] ornChMask = null;
								for (int k = i-1; k >=0; k--) {
									Integer[] prevMask = mask[k];
									// prevMask will contain only one index (or none), so only the
									// first index position needs to be checked
//									System.out.println(Arrays.toString(prevMask));
//									System.out.println((ind));
//									System.out.println(k);
//									System.out.println(prevMask[(ONSET_IND + 1) + 2*NUM_COURSES]);
									if (prevMask[(ONSET_IND + 1) + 2*NUM_COURSES] != null &&
										prevMask[(ONSET_IND + 1) + 2*NUM_COURSES] == ind) {
//									if (prevMask[(ONSET_IND + 1) + 2*NUM_COURSES] == ind) {
										ornChMask = prevMask;
										break;
									}
								}
								csv.add(ind + "," + ornPitches.get(j) + "," + 
									btp[ind][Tablature.CHORD_SEQ_NUM] + "," + 
//									ornChMask[0] + " " + Tablature.getMetricPosition(
//									new Rational(ornChMask[1], SMALLEST_DUR), meterInfo)[1] + "," +	
									ToolBox.getMetricPositionAsString(Tablature.getMetricPosition(
									new Rational(ornChMask[ONSET_IND], SMALLEST_DUR), meterInfo)) + "," +
									closestVoice + "," + "n/a" + "," + "ornamentation");
							}
						}
						currOrn.clear();
					}

					// Set information needed for assignment of any ornamental notes or consecutive
					// tuplet chords
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
		csv.add(0, "note,pitch,chord,bar,mapped voice,cost,category");
		csv.forEach(s -> resCsv.append(s + "\r\n"));
//		for (String s : csv) {
//			System.out.println(s);
//		}

//		System.out.println("voiceLabels");
//		for (int i = 0; i < voiceLabels.size(); i++) {
//			System.out.println("i = " + i + "; voice label = " + voiceLabels.get(i));
//		}

//		System.out.println(btp.length);
//		System.out.println(Collections.frequency(voiceLabels, null));
		
		int numNotes = btp.length - Collections.frequency(voiceLabels, null);
		int numNotesTrans = trans.getBasicNoteProperties().length;
		
		res.append("number of notes in tab:    " + numNotes + "\r\n");
		res.append("number of mismatches:      " + numMismatches + ", of which " + "\r\n" + 
				   "                           " + repetitionInd.size() + " repetitions" + "\r\n" +
				   "                           " + ornamentationInd.size() + " ornamentations" + "\r\n" +
				   "                           " + fictaInd.size() + " ficta" + "\r\n" +
				   "                           " + otherInd.size() + " other" + "\r\n");
		
		int Mo = ornamentationInd.size();
		int Mr = repetitionInd.size();
		int Mf = fictaInd.size();
		int Ma = otherInd.size();

		// See ISMIR paper
		// m_(o,r,f): all mismatches count
		double morf = (numNotes - (Mo + Mr + Mf + Ma)) / (double) numNotes; 
		// m_o: repetitions and ficta do not count
		double mo  = (numNotes - (Mo + Ma)) / (double) numNotes; 
		double m = (numNotes - Ma) / (double) numNotes;

		resultsOverAllPieces.append(
//			((shortName.length() < 4) ? shortName : shortName.substring(0, 3)) + "\t"  +
			shortNames.get(pieceIndex) + "\t" +
			numNotesTrans + "\t" + 	
			numNotes + "\t" + 
			numMismatches + "\t" + 
			ornamentationInd.size() + "\t" + 
			repetitionInd.size() + "\t" + 
			fictaInd.size() + "\t" + 
			otherInd.size() + "\t" +
			ToolBox.formatDouble(morf, 0, 5) + "\t" +
			ToolBox.formatDouble(mo, 0, 5) + "\t" +
			ToolBox.formatDouble(m, 0, 5) + 
			"\r\n"
		);
		Integer[] currInts = new Integer[]{
			null, 
			numNotesTrans, numNotes, numMismatches, 
			ornamentationInd.size(), repetitionInd.size(), fictaInd.size(), otherInd.size(),
			null, null, null
		};
		Double[] currDoubles = new Double[]{
			null, null, null, null, null, null, null, null,
			morf, mo, m
		};
		resultsOverAllPiecesArrStr[pieceIndex][0] = shortNames.get(pieceIndex); 
		for (int i = 0; i < currInts.length; i++) {
			if (currInts[i] != null) {
				resultsOverAllPiecesArrStr[pieceIndex][i] = String.valueOf(currInts[i]);
				intsToAvg[i] += currInts[i]; 
			}
			else if (currDoubles[i] != null) {
				resultsOverAllPiecesArrStr[pieceIndex][i] = ToolBox.formatDouble(currDoubles[i], 0, 5);
				doublesToAvg[i] += currDoubles[i];
			}
		}
//		resultsOverAllPiecesArr[pieceIndex] = new double[]{
//			pieceIndex, 
//			numNotesTrans, 	
//			numNotes,
//			numMismatches,
//			ornamentationInd.size(), 
//			repetitionInd.size(), 
//			fictaInd.size(),
//			otherInd.size(),
////			ToolBox.formatDouble(morf, 0, 5),
//			morf,
////			ToolBox.formatDouble(mo, 0, 5)
//			mo
////			ToolBox.formatDouble(m, 0, 5) + 
//		};

		res.append("percentage of matches:     " + 
			(1.0 - (numMismatches/(double) numNotes)) + " (only full matches)" + "\r\n");
		res.append("                           " + 
			(1.0 - (numMismatches-(repetitionInd.size() + fictaInd.size()))/(double) numNotes) + 
			" (including repetitions and ficta)" + "\r\n");
		res.append("                           " + 
			(1.0 - (numMismatches-(ornamentationInd.size() + repetitionInd.size() + 
			fictaInd.size()))/(double) numNotes) + " " + 
			"(including ornamentations, repetitions, ficta)" + "\r\n");
//		System.out.println(res);
		
		List<List<Integer>> mismatchInds = new ArrayList<>();
		mismatchInds.add(Transcription.INCORRECT_IND, null);
		mismatchInds.add(Transcription.ORNAMENTATION_IND, ornamentationInd);
		mismatchInds.add(Transcription.REPETITION_IND, repetitionInd);
		mismatchInds.add(Transcription.FICTA_IND, fictaInd);
		mismatchInds.add(Transcription.OTHER_IND, otherInd);
					
		return Arrays.asList(new Object[]{voiceLabels, mismatchInds, res.toString(), resCsv.toString()});
	}


	// F I R S T - O R D E R  H E L P E R S
	/**
	 * Returns grid and mask.
	 * 
	 * The grid contains, per chord: bar, onset, pitches per voice (starting at lowest voice), 
	 * durations per voice (starting at lowest voice).
	 * The mask contains, per chord: bar, onset, pitches (low to high), durations (i.e., minimum 
	 * duration), indices
	 * 
	 * @param trans
	 * @param tab
	 * @return
	 */
	private static List<Integer[][]> makeGridAndMask(Transcription trans, Tablature tab) {
		Integer[][] bnp = trans.getBasicNoteProperties();
		Integer[][] btp = tab.getBasicTabSymbolProperties();
		
		// Determine smallest duration in MIDI (currently simply set to Tablature.SMALLEST_RHYTHMIC_VALUE)
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

		// Get union of sets of onset times in tab and trans
		List<Rational> allOnsetTimes = new ArrayList<>(trans.getAllOnsetTimes());
//		System.out.println("allOnsetTimes trans");
//		for (Rational r : allOnsetTimes) {
//			System.out.println(r);
//		}
//		System.out.println("allOnsetTimes tab");
//		for (Rational r : tab.getAllOnsetTimes()) {
//			r.reduce();
//			System.out.println(r);
//		}
//		System.exit(0);
		for (Rational r : tab.getAllOnsetTimes()) {
			if (!allOnsetTimes.contains(r)) {
				allOnsetTimes.add(r);
			}
		}
		Collections.sort(allOnsetTimes);

		// Make grid; initialise with all values set to null  
		NotationSystem score = trans.getPiece().getScore();
		int numVoices = score.size();
		Integer[][] grid = new Integer[allOnsetTimes.size()][(ONSET_IND + 1) + 2*numVoices];

		// Set bars and onsets
		for (int i = 0; i < allOnsetTimes.size(); i++) {
			Rational onsetFrac = allOnsetTimes.get(i);
//			System.out.println("i, onsetFrac = " + i + ", " + onsetFrac);
//			System.out.println(onsetFrac);
//			System.out.println(Arrays.toString(Tablature.getMetricPosition(onsetFrac, tab.getMeterInfo())));
			grid[i][BAR_IND] = Tablature.getMetricPosition(onsetFrac, tab.getMeterInfo())[0].getNumer();
//			if (onsetFrac.equals(new Rational(197291, 1024))) {
//				System.out.println(onsetFrac.mul(smallestDur).getNumer());
//				System.exit(0);
//			}
//			Rational onsetRaw = onsetFrac.mul(smallestDur);
//			// The denominator should always be 1 because of multiplication with smallest rhythmic value;
//			// however, in the case of triplet rounding issues it can be not 1 
//			if (onsetRaw.getDenom() != 1) {
//				System.out.println("not 1!");
//				System.out.println(grid[i][BAR_IND]);
//				System.out.println(onsetRaw);
//				onsetRaw = roundFraction(onsetRaw);
//				System.out.println(onsetRaw);
//				System.exit(0);
//			}
//			grid[i][ONSET_IND] = onsetRaw.getNumer(); // denominator is always 1 because of multiplication with smallest rhythmic value
			grid[i][ONSET_IND] = onsetFrac.mul(smallestDur).getNumer(); // denominator is always 1 because of multiplication with smallest rhythmic value	
//			if (grid[i][ONSET_IND] == 591873) {
//				System.out.println("i = " + i);
//				System.exit(0);
//			}
		}
		// Set pitches and durations
		for (int i = numVoices - 1; i >= 0; i--) {
			NotationVoice nv = score.get(i).get(0);
			for (NotationChord nc : nv) {
				Note n = nc.get(0);
				int gridRowInd = allOnsetTimes.indexOf(n.getMetricTime()); 
				// Add pitch at index of voice i
				grid[gridRowInd][(ONSET_IND + 1) + ((numVoices-1)-i)] = n.getMidiPitch();
				// Add duration at index of voice i
				grid[gridRowInd][(ONSET_IND + 1) + numVoices + ((numVoices-1)-i)] = 
					n.getMetricDuration().mul(smallestDur).getNumer(); // denominator is always 1 because of multiplication with smallest rhythmic value
			}
		}
					
		// Make mask; initialise with all values set to null
		Integer[][] mask = new Integer[allOnsetTimes.size()][(ONSET_IND + 1) + 3*NUM_COURSES];
		
		// Set bars and onsets
		for (int i = 0; i < allOnsetTimes.size(); i++) {
			Rational onsetFrac = allOnsetTimes.get(i);
			mask[i][BAR_IND] = Tablature.getMetricPosition(onsetFrac, tab.getMeterInfo())[0].getNumer();
			mask[i][ONSET_IND] = onsetFrac.mul(smallestDur).getNumer(); // denominator is always 1 because of multiplication with smallest rhythmic value	
		}
		// Set pitches and durations
		for (int i = 0; i < btp.length; i++) {
			int onset = btp[i][Tablature.ONSET_TIME];
			Rational[] posInBar = Tablature.getMetricPosition(new Rational(onset, smallestDur), tab.getMeterInfo());
			int bar = posInBar[0].getNumer();
			Rational pos = posInBar[1];
			pos.reduce();
//			System.out.println("i = " + i + "; bar " + bar + "; pos " + pos);
			Integer[] currRow = mask[allOnsetTimes.indexOf(new Rational(onset, smallestDur))];
			int chordSize = btp[i][Tablature.CHORD_SIZE_AS_NUM_ONSETS];
			for (int j = i; j < i + chordSize; j++) {
				currRow[(ONSET_IND + 1) + (j-i)] = btp[j][Tablature.PITCH];
				currRow[(ONSET_IND + 1) + NUM_COURSES + (j-i)] = btp[j][Tablature.MIN_DURATION];
				currRow[(ONSET_IND + 1) + 2*NUM_COURSES + (j-i)] = j;
			}
			i += (chordSize-1);
		}
		return Arrays.asList(new Integer[][][]{grid, mask});
	}


	/**
	 * Maps the pitches in the tablature chord to a voice by finding their counterpart in the 
	 * MIDI chord. Returns a list containing
	 * as element 0: a list of lists, containing 
	 *               as element 0: the tab pitches not in the MIDI
	 *               as element 1: the indices of the tab pitches not in the MIDI
	 *               as element 2: the mapped voices in the chord
	 *               as element 3: the active voices in the chord
	 *               as element 4: any SNU pitches that could not be mapped due to lack of space 
	 *                             in the chord
	 *               as element 5: the voices going with any extended SNU
	 * as element 1: a list of voice labels the size of pitchesTab, containing the label for each 
	 *               mapped pitch and <code>null</code> for each unmapped pitch 
	 * @param pitchesTab
	 * @param indicesTab
	 * @param pitchesGT
	 * @param numVoices
	 * @return
	 */
	private static List<Object> mapTabToMIDI(List<Integer> pitchesTab, 
		List<Integer> indicesTab, List<Integer> pitchesGT, int numVoices) {
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
//			if (Collections.frequency(pitchesTab, pitchInTab) > 2) {
//				throw new RuntimeException("Pitch " + pitchInTab + " occurs more than twice (bar " +
//					currGrid[ONSET_IND] + "; onset " + currOnset + ".");
//			}
			List<Double> currVoiceLabel = new ArrayList<Double>(emptyVoiceLabel);

			// Map the pitch to (a) voice(s) and make a voice label
			//
			// in tab 	in MIDI			
			// 1		0		--> unmapped pitch
			// 1		1		--> mapped pitch
			// 1		2		--> mapped SNU
			// 2		0		--> unmapped unison (assumed to be rare)
			// 2		1		--> half-mapped unison (assumed not to happen)
			// 2		2		--> mapped unison
			for (int j = 0; j < pitchesGT.size(); j++) {
				if (pitchesGT.get(j) != null && pitchesGT.get(j) == pitchInTab) {
					int voice = (numVoices-1) - j;
//					System.out.println("j = " + j);
//					System.out.println("--> pitchesGT.get(j) = " + pitchesGT.get(j));
//					System.out.println("--> voice = " + voice);

					int freqInTab = Collections.frequency(pitchesTab, pitchInTab); 
					int freqInGT = Collections.frequency(pitchesGT, pitchInTab);

					// In case of a possible SNU
					if (freqInTab == 1 && freqInGT == 2) {
//-*-						System.out.println("SNU!!");
						// If there is room for a SNU: set voice
						if (pitchesTab.size() < numVoices) {
							currVoiceLabel.set(voice, 1.0);
						}
						// If there is no room for a SNU: do not set voice (pitchInTab 
						// is added to pitchesNotInMIDI)
						// NB This is assumed not to happen in an extended SNU case
						else {
							nonMappedSNUPitches.add(pitchInTab);
						}
					}
					// In case of an extended SNU (a single note assigned to more than two voices)
					// NB It is assumed that there will always be room for at least one SNU
					else if (freqInTab == 1 && freqInGT > 2) {
//-*-						System.out.println("extended SNU!!");
						currVoiceLabel.set(voice, 1.0);
					}

					// In case of a unison: add only if first and last indices in lists align
					else if (freqInTab == 2 && freqInGT == 2) {
//-*-						System.out.println("unison!!");
						if (j == pitchesGT.indexOf(pitchInTab) && i == pitchesTab.indexOf(pitchInTab) || 
							j == pitchesGT.lastIndexOf(pitchInTab) && i == pitchesTab.lastIndexOf(pitchInTab)) {
							currVoiceLabel.set(voice, 1.0);
						}
					}
					// In case of a unison and a SNU: the second unison note is a SNU
					// NB: freqInTab can only be 2 in case of a unsion
					// NB2: it is assumed that freqInGT == 3
					// Examples:
					// Inviolata, pt. 2, b. 76: f-f-f = 5256_05_...-2, b. 13: f-f
					// Preter, pt.2, b. 143: d-d-d-a  = 5253_02_...-2, b. 56: d-d-a
					//                                = 5694_03_...-2, b. 56: d-d-a
					// Preter, pt.2, b. 164: d-d-d-a  = 5694_03_...-2, b. 77: d-d-a
					// Je ne me puis, b. 54: a-a-a    = 5260_09      , b. 54: a-a-e
					else if (freqInTab == 2 && freqInGT > 2) {
//-*-						System.out.println("unison + SNU!!");
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
					// In case of a non-SNU and non-unison note
					else {
						currVoiceLabel.set(voice, 1.0);
					}
					// If the voice has been set: add to mapped voices
					if (currVoiceLabel.get(voice) != 0.0 && !mappedVoices.contains(voice)) {
						mappedVoices.add(voice);
					}
					// Add to active voices if not done yet
					if (currVoiceLabel.get(voice) != 0.0 && !activeVoices.contains(voice)) {
						activeVoices.add(voice);
//						Collections.sort(activeVoices);
//						Collections.reverse(activeVoices);
					}
				}
			}
			// Add the voice label to voiceLabelsCurrChord
			if (!currVoiceLabel.equals(emptyVoiceLabel)) {
				voiceLabelsChord.add(currVoiceLabel);
			}
			else {
//				System.out.println("pitch " + pitchInTab + " (pitchInd " + pitchInd + ") not found!");
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
	 * closest last pitch (in semitones) in all voices. Returns a list containing
	 * as element 0: a list of lists, containing 
	 *               as element 0: the tab pitches not in the MIDI
	 *               as element 1: the indices of the tab pitches not in the MIDI
	 *               as element 2: the tab pitches not in the MIDI, in its original (complete) form
	 *               as element 3: the indices of any pitches flagged as repetition
	 *               as element 4: the indices of any pitches flagged as ficta
	 *               as element 5: the indices of any pitches flagged as other
	 * as element 1: a list of voice labels the size of pitchesTab, containing the label for each 
	 *               mapped pitch
	 * as element 2: a list containing, for each tab pitch not in the MIDI
	 *               as element 0: the voice it has been assigned to
	 *               as element 1: the pitch
	 *               as element 2: the cost at which it has been assigned to the voice
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
	 *                    tuplet chord
	 * @param prevVoiceLabels Non-<code>null</code> when the chord is possibly a consecutive
	 *                        tuplet chord                           
	 * @return
	 */
	static List<Object> mapPitchesNotInMIDI(List<Integer> pitchesTab, List<Integer> pitchesGT,
		List<Integer> pitchesNotInMIDI, List<Integer> indPitchesNotInMIDI,
		List<Integer> pitchesNotInMIDIOriginal, List<Integer> nonMappedSNUPitches,
		List<Integer> extendedSNUVoices, List<Integer> mappedVoices, List<List<Double>> 
		voiceLabelsCurrChord, List<Integer[]> keyInfo, Rational currOnset, Transcription trans,
		List<Integer> prevPitches, List<List<Double>> prevVoiceLabels){

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
		int keySig = key[0]; // num b (<0) / # (>0) 
		int scale = key[1]; // major (0) / minor (1)
		int base = KEY_SIGS.get(keySig)[scale]%12;
		List<Integer[]> fictaPairs = (scale == 0) ? fictaPairsMajor : fictaPairsMinor;
//		List<Integer> intervals = (scale == 0) ? new ArrayList<Integer>(MAJOR) : new ArrayList<Integer>(MINOR);		
//		List<Integer> baseIntervals = new ArrayList<>();
//		intervals.forEach((interval) -> baseIntervals.add((interval + KEY_SIGS.get(keySig)[scale])%12));
		
		List<Integer> repetitionInd = new ArrayList<>();
		List<Integer> fictaInd = new ArrayList<>();
		List<Integer> otherInd = new ArrayList<>();
		
		// Find the cheapest mapping of pitchesNotInMIDI. In three cases, two 
		// iterations are needed, and in one special case three; otherwise only one:
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
//-*-				System.out.println("yes");
				if (prevPitches != null && prevPitches.size() == pitchesTab.size()) {
					isConsecutiveTupletChord = true;
					System.out.println("isConsecutiveTupletChord in " + trans.getPieceName());
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

			// In the case of a non-mapped SNU: if this pitch is mapped onto one of the two voices 
			// with the same pitch in the GT, it is not a mismatch and can be removed from cheapestMapping
			// NB In the case of two iterations, only the iteration in which the pitch is mapped 
			// must be checked; this is what the part after the && is for
			List<Integer> currMappedPitches = ToolBox.getItemsAtIndex(cheapestMapping, 1); 
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
						DataConverter.convertIntoVoiceLabel(Arrays.asList(new Integer[]{currNonMappedSNUVoice})));
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
							DataConverter.convertIntoListOfVoices(
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
				// Repetition
				if (cost == 0) {
					repetitionInd.add(pitchInd);
				}
				// Ficta
				else if	(
					// sharp ficta
					pitchesGT.contains(pitch-1) && fictaPairs.stream().anyMatch(a -> 
					Arrays.equals(a, new Integer[]{(pitch-base)%12, ((pitch-1)-base)%12}))
					||
					// flat ficta
					pitchesGT.contains(pitch+1) && fictaPairs.stream().anyMatch(a -> 
					Arrays.equals(a, new Integer[]{(pitch-base)%12, ((pitch+1)-base)%12}))) {	
					fictaInd.add(pitchInd);
				}
				// Residual cases
				else {
					otherInd.add(pitchInd);
				}
			}

			// 7. Replace null voice labels with voice labels for unmapped pitches.
			// voiceLabelsCurrChord is aligned with pitchesTab; the element to replace is 
			// determined by finding the index of the unmapped pitch in pitchesTab
			for (Integer[] in : cheapestMapping) {
				int pitch = in[1];
				int voice = in[0];
				List<Double> label = 
					DataConverter.convertIntoVoiceLabel(Arrays.asList(new Integer[]{voice}));
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
				int voiceForP = DataConverter.convertIntoListOfVoices(
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
		intLists.add(repetitionInd);
		intLists.add(fictaInd);
		intLists.add(otherInd);	
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
	 * Gets, for each voice in the given voices, the pitch of the last note before the given onset.
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
		NotationSystem ns = trans.getPiece().getScore();
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
	 * @return A List<Integer[]> representing the cheapest mapping for all pitches, and each element 
	 *         of which contains
	 *         as element (0): the voice the pitch is mapped to
	 *         as element (1): the pitch 
	 *         as element (2): the cost () of mapping the pitch to the voice
	 */
	// TESTED
	static List<Integer[]> getCheapestMapping(List<Integer> pitches, 
		List<List<Integer[]>> comb, List<Integer[]> lastPitchInAvailableVoices) {
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
//				System.out.println(Arrays.toString(c));
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
			if (currTotalCost < cheapest) {
				cheapest = currTotalCost;
				cheapestMapping = currMapping;
//				if (currCheapest == 0) {
//					numOfTieRepetitions++;
//				}
//				for (Integer[] in : currMapping) {
//					if (in[2] == 0) {
//						numOfTieRepetitions++;
//					}
//				}
				
			}
///-			System.out.println("currCost = " + currTotalCost);
///-			System.out.println("currMapping:");
			for(Integer[] in : currMapping) {
///-				System.out.println(Arrays.toString(in));
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
						DataConverter.convertIntoListOfVoices(voiceLabels.get(j)));
				}
			}
			pitchesAndVoices.add(closestPitches);
			pitchesAndVoices.add(closestVoices);
			return pitchesAndVoices;
		}
	}


	// C O N V E N I E N C E  M E T H O D S
	private static List<String[]> getPieces() {
		List<String[]> pieces = Arrays.asList(new String[][]{
			// Two Morales test pieces from https://www.uma.es/victoria/morales.html
//			new String[] {"3610_033_inter_natos_mulierum_morales_T-rev", "Morales-Inter_Natos_Mulierum-1-54"},
//			new String[] {"3618_041_benedictus_from_missa_de_l_homme_arme_morales_T", "Morales-Lhomme_Arme-a4-5-Benedictus"},
			
			// Tours
//			new String[]{"1132_13_o_sio_potessi_donna_berchem_solo", "Berchem_-_O_s'io_potessi_donna"}
			
			// Tab reconstruction project 
//			new String[]{"ah_golden_hairs-NEW", "ah_golden_hairs-NEW"},
//			new String[]{"an_aged_dame-II", "an_aged_dame-II"},
//			new String[]{"as_caesar_wept-II", "as_caesar_wept-II"},
//			new String[]{"blame_i_confess-II", "blame_i_confess-II"},
////			new String[]{"delight_is_dead-II", "delight_is_dead-II"},
//			new String[]{"in_angels_weed-II", "in_angels_weed-II"},
//			new String[]{"o_lord_bow_down-II", "o_lord_bow_down-II"},
//			new String[]{"o_that_we_woeful_wretches-NEW", "o_that_we_woeful_wretches-NEW"},
//			new String[]{"quis_me_statim-II", "quis_me_statim-II"},
//			new String[]{"rejoyce_unto_the_lord-NEW", "rejoyce_unto_the_lord-NEW"},
//			new String[]{"sith_death-NEW", "sith_death-NEW"},
//			new String[]{"the_lord_is_only_my_support-NEW", "the_lord_is_only_my_support-NEW"},
//			new String[]{"the_man_is_blest-NEW", "the_man_is_blest-NEW"},
//			new String[]{"while_phoebus-II", "while_phoebus-II"},
			
			// JosquIntab
			// a. Mass sections
//			new String[]{"4471_40_cum_sancto_spiritu", "Jos0303b-Missa_De_beata_virgine-Gloria-222-248"},
//			new String[]{"5266_15_cum_sancto_spiritu_desprez", "Jos0303b-Missa_De_beata_virgine-Gloria-222-248"},
//			new String[]{"3643_066_credo_de_beata_virgine_jospuin_T-1", "Jos0303c-Missa_De_beata_virgine-Credo-1-102"},
//			new String[]{"3643_066_credo_de_beata_virgine_jospuin_T-2", "Jos0303c-Missa_De_beata_virgine-Credo-103-159"},
//			new String[]{"5106_10_misa_de_faysan_regres_2_gloria", "Jos0801b-Missa_Faisant_regretz-Gloria-37-94"},
//			new String[]{"5189_16_sanctus_and_hosanna_from_missa_faisant_regrets-1", "Jos0801d-Missa_Faisant_regretz-Sanctus-1-22"},
//			new String[]{"5107_11_misa_de_faysan_regres_pleni", "Jos0801d-Missa_Faisant_regretz-Sanctus-23-67"},
//			new String[]{"5189_16_sanctus_and_hosanna_from_missa_faisant_regrets-2", "Jos0801d-Missa_Faisant_regretz-Sanctus-68-97"},
//			new String[]{"5188_15_sanctus_and_hosanna_from_missa_hercules-1", "Jos1101d-Missa_Hercules_dux_Ferrarie-Sanctus-1-17"},
//			new String[]{"3584_001_pleni_missa_hercules_josquin", "Jos1101d-Missa_Hercules_dux_Ferrarie-Sanctus-18-56"},
//			new String[]{"5188_15_sanctus_and_hosanna_from_missa_hercules-2", "Jos1101d-Missa_Hercules_dux_Ferrarie-Sanctus-57-88"},
//			new String[]{"3585_002_benedictus_de_missa_pange_lingua_josquin", "Jos0403d-Missa_Pange_lingua-Sanctus-139-186"},
//j-last	
//			new String[]{"5190_17_cum_spiritu_sanctu_from_missa_sine_nomine", "Jos1202b-Missa_Sine_nomine-Gloria-103-132"},
			
			// b. Motets
//			new String[]{"5265_14_absalon_fili_me_desprez", "Jos1401-Absalon_fili_mi"},
//			new String[]{"3647_070_benedicta_est_coelorum_josquin_T", "Jos2313-Benedicta_es_celorum-1-107"},
//			new String[]{"4964_01a_benedictum_es_coelorum_josquin", "Jos2313-Benedicta_es_celorum-1-107"},
//j-last	
//			new String[]{"4965_01b_per_illud_ave_josquin", "Jos2313-Benedicta_es_celorum-108-135"},
//			new String[]{"4966_01c_nunc_mater_josquin", "Jos2313-Benedicta_es_celorum-136-176"},
//			new String[]{"5254_03_benedicta_es_coelorum_desprez-1", "Jos2313-Benedicta_es_celorum-1-107"},
//			new String[]{"5254_03_benedicta_es_coelorum_desprez-2", "Jos2313-Benedicta_es_celorum-108-135"},
//			new String[]{"5254_03_benedicta_es_coelorum_desprez-3", "Jos2313-Benedicta_es_celorum-136-176"},
			// TODO 0, 1, 32, 33, 36, 38
//j-mul		
//			new String[]{"5702_benedicta-1", "Jos2313-Benedicta_es_celorum-1-107"},
			// TODO 91, 93, 222, 226, 231, 234
//j-mul	
//			new String[]{"5702_benedicta-2", "Jos2313-Benedicta_es_celorum-108-135"},
//			new String[]{"5702_benedicta-3", "Jos2313-Benedicta_es_celorum-136-176"},
//			new String[]{"3591_008_fecit_potentiam_josquin", "Jos2004-Magnificat_Quarti_toni-Verse_6_Fecit_potentiam"},
//			new String[]{"5263_12_in_exitu_israel_de_egipto_desprez-1", "Jos1704-In_exitu_Israel_de_Egypto-1-143"},
//			new String[]{"5263_12_in_exitu_israel_de_egipto_desprez-2", "Jos1704-In_exitu_Israel_de_Egypto-144-280"},
			new String[]{"5263_12_in_exitu_israel_de_egipto_desprez-3", "Jos1704-In_exitu_Israel_de_Egypto-281-401"},
//			new String[]{"5256_05_inviolata_integra_desprez-1", "Jos2404-Inviolata_integra_et_casta_es-1-63"},
//			new String[]{"5256_05_inviolata_integra_desprez-2", "Jos2404-Inviolata_integra_et_casta_es-64-105"},
//			new String[]{"5256_05_inviolata_integra_desprez-3", "Jos2404-Inviolata_integra_et_casta_es-106-144"},
//			new String[]{"4465_33-34_memor_esto-1", "Jos1714-Memor_esto_verbi_tui-1-165"},
//			new String[]{"4465_33-34_memor_esto-2", "Jos1714-Memor_esto_verbi_tui-166-325"},
//			new String[]{"932_milano_108_pater_noster_josquin-1", "Jos2009-Pater_noster-1-120"},
//			new String[]{"932_milano_108_pater_noster_josquin-2", "Jos2009-Pater_noster-121-198"},
//			new String[]{"5252_01_pater_noster_desprez-1", "Jos2009-Pater_noster-1-120"},
//j-once	
//			new String[]{"5252_01_pater_noster_desprez-2", "Jos2009-Pater_noster-121-198"},
//			new String[]{"3649_072_praeter_rerum_seriem_josquin_T", "Jos2411-Preter_rerum_seriem-1-87"},		
//			new String[]{"5253_02_praeter_rerum_seriem_desprez-1", "Jos2411-Preter_rerum_seriem-1-87"},
//			new String[]{"5253_02_praeter_rerum_seriem_desprez-2", "Jos2411-Preter_rerum_seriem-88-185"},
//			new String[]{"5694_03_motet_praeter_rerum_seriem_josquin-1", "Jos2411-Preter_rerum_seriem-1-87"},
//j-once	
//			new String[]{"5694_03_motet_praeter_rerum_seriem_josquin-2", "Jos2411-Preter_rerum_seriem-88-185"},
//j-once	
//			new String[]{"1274_12_qui_habitat_in_adjutorio-1", "Jos1807-Qui_habitat_in_adjutorio_altissimi-1-155"},
//			new String[]{"1274_12_qui_habitat_in_adjutorio-2", "Jos1807-Qui_habitat_in_adjutorio_altissimi-156-282"},
//			new String[]{"5264_13_qui_habitat_in_adjutorio_desprez-1", "Jos1807-Qui_habitat_in_adjutorio_altissimi-1-155"},
//			new String[]{"5264_13_qui_habitat_in_adjutorio_desprez-2", "Jos1807-Qui_habitat_in_adjutorio_altissimi-156-282"},
			// TODO 0, 1
//j-mul		
//			new String[]{"933_milano_109_stabat_mater_dolorosa_josquin", "Jos2509-Stabat_mater__Comme_femme-1-88"},
//			new String[]{"5255_04_stabat_mater_dolorosa_desprez-1", "Jos2509-Stabat_mater__Comme_femme-1-88"},			
//			new String[]{"5255_04_stabat_mater_dolorosa_desprez-2", "Jos2509-Stabat_mater__Comme_femme-89-180"},

			// c. Chansons
//			new String[]{"4400_45_ach_unfall_was", "Jos2829-Qui_belles_amours"},
//			new String[]{"4481_49_ach_unfal_wes_zeigst_du_mich", "Jos2829-Qui_belles_amours"},
//			new String[] {"4406_51_adieu_mes_amours", "Jos2803-Adieu_mes_amours"},
//			new String[] {"4467_37_adieu_mes_amours", "Jos2803-Adieu_mes_amours"},
//			new String[] {"1025_adieu_mes_amours", "Jos2803-Adieu_mes_amours-anacrusis"},
//j-once	
//			new String[] {"1030_coment_peult_avoir_joye", "Jos2807-Comment_peult_avoir_joye"},
//			new String[] {"1275_13_faulte_d_argent", "Jos2907-Faulte_dargent"},
//			new String[] {"3638_061_lauda_sion_gombert_T", "Jos2911-Je_ne_me_puis_tenir_daimer"},
//			new String[] {"5148_51_respice_in_me_deus._F#_lute_T", "Jos2911-Je_ne_me_puis_tenir_daimer"},
//j-once	
//			new String[] {"5260_09_date_siceram_morentibus_sermisy", "Jos2911-Je_ne_me_puis_tenir_daimer"},
//			new String[] {"4438_07_la_plus_des_plus", "Jos2722-La_plus_des_plus"},
////			new String[] {"4443_12_la_bernardina", "Jos2721-La_Bernardina"},
////			new String[] {"1033_la_bernadina_solo_orig", "Jos2721-La_Bernardina"},
//j-once	
//			new String[] {"5191_18_mille_regres", "Jos2825-Mille_regretz"},
//			new String[] {"4482_50_mille_regrets_P", "Jos2825-Mille_regretz"},
//			new String[] {"4469_39_plus_nulz_regrets_P", "Jos2828-Plus_nulz_regrets"},
//			new String[] {"922_milano_098_que_voulez_vous_dire_de_moi", "Jos2832-Si_jay_perdu"},
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


//	private static void fitDuration(File f, double diminution) {
//		f = new File("C:/Users/Reinier/Desktop/ISMIR-2019/test/tab/3618_041_benedictus_from_missa_de_l_homme_arme_morales_T.tbp");
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
//			new File("C:/Users/Reinier/Desktop/ISMIR-2019/test/tab/3618_041_benedictus_from_missa_de_l_homme_arme_morales_T-halved.tbp"));
//		System.exit(0);
//	}

}
