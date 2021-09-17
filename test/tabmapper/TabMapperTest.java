package tabmapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.uos.fmt.musitech.utility.math.Rational;
import exports.MEIExport;
import junit.framework.TestCase;
import representations.Tablature;
import representations.Transcription;
import tools.ToolBox;

public class TabMapperTest extends TestCase {

	private File midiTestpiece;
	private File encodingTestpiece;
	
	protected void setUp() throws Exception {
		super.setUp();
//		Runner.setPathsToCodeAndData(UI.getRootDir(), false);
//		encodingTestpiece = new File(Runner.encodingsPathTest + "testpiece.tbp");
//		midiTestpiece = new File(Runner.midiPathTest + "testpiece.mid");
		encodingTestpiece = new File(MEIExport.rootDir + "data/annotated/encodings/test/" + "testpiece.tbp");
		midiTestpiece = new File(MEIExport.rootDir + "data/annotated/MIDI/test/" + "testpiece.mid");
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}


	public void testFindClosestMultiple() {
		Rational gridVal = new Rational(1, 96);
		List<Rational> onsets = Arrays.asList(new Rational[]{
			new Rational(1, 2), // is equal to multiple
			new Rational(3, 7), // closest multiple below actual value
			new Rational(22, 9), // closest multiple above actual value
			new Rational(17, 192), // closest multiple below and above actual value equally close
		});

		List<Rational> expected = Arrays.asList(new Rational[]{
			new Rational(48, 96),
			new Rational(41, 96),
			new Rational((2*96)+43, 96),
			new Rational(9, 96)
		});

		List<Rational> actual = new ArrayList<>();
		for (Rational r : onsets) {
			actual.add(TabMapper.findClosestMultiple(r, gridVal));
		}

		assertEquals(expected, actual);
	}


	public void testFindClosestPitchesAndVoices() {
		Transcription trans = new Transcription(midiTestpiece, encodingTestpiece);
		Tablature tab = new Tablature(encodingTestpiece, false);
		
		List<List<List<Integer>>> expected = new ArrayList<>();
		
		// g'# to chord 1
		int lastOrnPitch1 = 68;
		List<Integer> pitchesTab1 = tab.getPitchesInChord(1);
		List<List<Double>> voiceLabels1 = trans.getChordVoiceLabels().get(1);
		List<List<Integer>> expected1 = new ArrayList<>();
		expected1.add(Arrays.asList(new Integer[]{69}));
		expected1.add(Arrays.asList(new Integer[]{1}));
		expected.add(expected1);

		// e' to chord 3 (has SNU)
		int lastOrnPitch3 = 64;
		List<Integer> pitchesTab3 = tab.getPitchesInChord(3);
		List<List<Double>> voiceLabels3 = trans.getChordVoiceLabels().get(3);
		List<List<Integer>> expected3 = new ArrayList<>();
		expected3.add(Arrays.asList(new Integer[]{65}));
		expected3.add(Arrays.asList(new Integer[]{0, 1}));
		expected.add(expected3);
		
		// g to chord 5 (has unison)
		int lastOrnPitch5 = 55;
		List<Integer> pitchesTab5 = tab.getPitchesInChord(5);
		List<List<Double>> voiceLabels5 = trans.getChordVoiceLabels().get(5);
		List<List<Integer>> expected5 = new ArrayList<>();
		expected5.add(Arrays.asList(new Integer[]{57, 57}));
		expected5.add(Arrays.asList(new Integer[]{3, 2}));
		expected.add(expected5);
		
		// eb to chord 8 (equal distance to voices 3 and 2)
		int lastOrnPitch8 = 51;
		List<Integer> pitchesTab8 = tab.getPitchesInChord(8);
		List<List<Double>> voiceLabels8 = trans.getChordVoiceLabels().get(8);
		List<List<Integer>> expected8 = new ArrayList<>();
		expected8.add(Arrays.asList(new Integer[]{45, 57}));
		expected8.add(Arrays.asList(new Integer[]{3, 2}));
		expected.add(expected8);
		
		// d1 to chord 3 (equals distance to SNU of voices 1 and 0)
		int lastOrnPitch3b = 62;
		List<Integer> pitchesTab3b = tab.getPitchesInChord(3);
		List<List<Double>> voiceLabels3b = trans.getChordVoiceLabels().get(3);
		List<List<Integer>> expected3b = new ArrayList<>();
		expected3b.add(Arrays.asList(new Integer[]{59, 65}));
		expected3b.add(Arrays.asList(new Integer[]{2, 0, 1}));
		expected.add(expected3b);
		
		List<List<List<Integer>>> actual = new ArrayList<>();
		actual.add(TabMapper.findClosestPitchesAndVoices(lastOrnPitch1, pitchesTab1, voiceLabels1));
		actual.add(TabMapper.findClosestPitchesAndVoices(lastOrnPitch3, pitchesTab3, voiceLabels3));
		actual.add(TabMapper.findClosestPitchesAndVoices(lastOrnPitch5, pitchesTab5, voiceLabels5));
		actual.add(TabMapper.findClosestPitchesAndVoices(lastOrnPitch8, pitchesTab8, voiceLabels8));
		actual.add(TabMapper.findClosestPitchesAndVoices(lastOrnPitch3b, pitchesTab3b, voiceLabels3b));
		
		assertEquals(expected.size(), actual.size());
		for (int i = 0; i < expected.size(); i++) {
			assertEquals(expected.get(i).size(), actual.get(i).size());
			for (int j = 0; j < expected.get(i).size(); j++) {
				assertEquals(expected.get(i).get(j).size(), actual.get(i).get(j).size());
				for (int k = 0; k < expected.get(i).get(j).size(); k++) {
					assertEquals(expected.get(i).get(j).get(k), actual.get(i).get(j).get(k));
				}
			}
		}
		assertEquals(expected, actual);
	}


	public void testRoundFraction() {
		List<Rational> rats = Arrays.asList(new Rational[]{
			new Rational(17, 4), // -1 gives 16/4 = 4/1
			new Rational(606, 32), // +2 gives 608/32 = 19/1 
			new Rational(795, 24), // -3 gives 792/24 = 33/1 
			new Rational(828, 64) // +4 gives 832/64 = 13/1 
		});
		
		List<Rational> expected = Arrays.asList(new Rational[]{
			new Rational(4, 1), 
			new Rational(19, 1), 
			new Rational(33, 1), 
			new Rational(13, 1) 
		});
		
		List<Rational> actual = new ArrayList<>();
		for (Rational r : rats) {
			actual.add(TabMapper.roundFraction(r));
		}
		
		assertEquals(expected.size(), actual.size());
		for (int i = 0; i < expected.size(); i++) {
			assertEquals(expected.get(i), actual.get(i));
		}
		assertEquals(expected, actual);
	}


	public void testGetLastPitchInVoices() {
		Transcription trans = new Transcription(midiTestpiece, null);
		List<Integer> availableVoices = Arrays.asList(new Integer[]{0, 1, 4});
		List<Rational> onsets = Arrays.asList(new Rational[]{
			new Rational(3, 4), 
			new Rational(4, 4), 
			new Rational(9, 8), 
			new Rational(31, 16), 
			new Rational(8, 4)
		});

		List<List<Integer[]>> expected = new ArrayList<>();
		// onset 3/4
		List<Integer[]> threeFour = new ArrayList<>();
//		threeFour.add(null);
//		threeFour.add(null);
//		threeFour.add(null);
		expected.add(threeFour);
		// Onset 4/4
		List<Integer[]> fourFour = new ArrayList<>();
//		fourFour.add(null);
		fourFour.add(new Integer[]{1, 65});
		fourFour.add(new Integer[]{0, 69});
		expected.add(fourFour);
		// Onset 9/8
		List<Integer[]> nineEight = new ArrayList<>();
//		nineEight.add(null);
		nineEight.add(new Integer[]{1, 69});
		nineEight.add(new Integer[]{0, 72});
		expected.add(nineEight);
		// Onset 31/16
		List<Integer[]> thirtyoneSixteen = new ArrayList<>();
		thirtyoneSixteen.add(new Integer[]{4, 45});
		thirtyoneSixteen.add(new Integer[]{1, 69});
		thirtyoneSixteen.add(new Integer[]{0, 68});
		expected.add(thirtyoneSixteen);
		// Onset 8/4
		List<Integer[]> eightFour = new ArrayList<>();
		eightFour.add(new Integer[]{4, 45});
		eightFour.add(new Integer[]{1, 69});
		eightFour.add(new Integer[]{0, 68});
		expected.add(eightFour);
		
		List<List<Integer[]>> actual = new ArrayList<>();
		for (Rational r : onsets) {
			actual.add(TabMapper.getLastPitchInVoices(availableVoices, 5, r, trans));
		}
		
		assertEquals(expected.size(), actual.size());
		for (int i = 0; i < expected.size(); i++) {			
			assertEquals(expected.get(i).size(), actual.get(i).size());
			for (int j = 0; j < expected.get(i).size(); j++) {
//				if (expected.get(i).get(j) == null) {
//					assertEquals(expected.get(i).get(j), actual.get(i).get(j));
//				}
//				else {
					assertEquals(expected.get(i).get(j).length, actual.get(i).get(j).length);
					for (int k = 0; k < expected.get(i).get(j).length; k++) {
						assertEquals(expected.get(i).get(j)[k], actual.get(i).get(j)[k]);
					}
//				}
			}
		}
	}


	public void testGetCheapestMapping() {
		List<Integer[]> lastPitchInAvailableVoices = new ArrayList<>();
		lastPitchInAvailableVoices.add(new Integer[]{3, 48});
		lastPitchInAvailableVoices.add(new Integer[]{2, 56});
		lastPitchInAvailableVoices.add(new Integer[]{1, 60});
		lastPitchInAvailableVoices.add(new Integer[]{0, 64});

		List<List<Integer>> allPitches = new ArrayList<>();
		allPitches.add(Arrays.asList(new Integer[]{62, null, null, null}));
		allPitches.add(Arrays.asList(new Integer[]{48, 61, null, null}));
		allPitches.add(Arrays.asList(new Integer[]{48, 56, 60, null}));
		allPitches.add(Arrays.asList(new Integer[]{50, 56, 61, 65}));

		List<Integer[]> lastPitchInAvailableVoicesSNU = new ArrayList<>();
		lastPitchInAvailableVoicesSNU.add(new Integer[]{3, 48});
		lastPitchInAvailableVoicesSNU.add(new Integer[]{2, 56});
		lastPitchInAvailableVoicesSNU.add(new Integer[]{1, 60});
		lastPitchInAvailableVoicesSNU.add(new Integer[]{0, 60});

		List<List<Integer[]>> comb = ToolBox.getCombinations(lastPitchInAvailableVoices.size());

		List<List<Integer[]>> expected = new ArrayList<>();	
		List<Integer[]> one = new ArrayList<>();
		one.add(new Integer[]{1, 62, 2});
		expected.add(one);
		List<Integer[]> two = new ArrayList<>();
		two.add(new Integer[]{3, 48, 0});
		two.add(new Integer[]{1, 61, 1});
		expected.add(two);
		List<Integer[]> three = new ArrayList<>();
		three.add(new Integer[]{3, 48, 0});
		three.add(new Integer[]{2, 56, 0});
		three.add(new Integer[]{1, 60, 0});
		expected.add(three);
		List<Integer[]> four = new ArrayList<>();
		four.add(new Integer[]{3, 50, 2});
		four.add(new Integer[]{2, 56, 0});
		four.add(new Integer[]{1, 61, 1});
		four.add(new Integer[]{0, 65, 1});
		expected.add(four);

		// With SNU	
		one = new ArrayList<>();
		one.add(new Integer[]{1, 62, 2});
		expected.add(one);
		two = new ArrayList<>();
		two.add(new Integer[]{3, 48, 0});
		two.add(new Integer[]{1, 61, 1});
		expected.add(two);
		three = new ArrayList<>();
		three.add(new Integer[]{3, 48, 0});
		three.add(new Integer[]{2, 56, 0});
		three.add(new Integer[]{1, 60, 0});
		expected.add(three);
		four = new ArrayList<>();
		four.add(new Integer[]{3, 50, 2});
		four.add(new Integer[]{2, 56, 0});
		four.add(new Integer[]{1, 61, 1});
		four.add(new Integer[]{0, 65, 5});
		expected.add(four);

		List<List<Integer[]>> actual = new ArrayList<>();
		for (List<Integer> pitches : allPitches) {
			actual.add(TabMapper.getCheapestMapping(pitches, comb, lastPitchInAvailableVoices));
		}
		for (List<Integer> pitches : allPitches) {
			actual.add(TabMapper.getCheapestMapping(pitches, comb, lastPitchInAvailableVoicesSNU));
		}

		assertEquals(expected.size(), actual.size());
		for (int i = 0; i < expected.size(); i++) {			
			assertEquals(expected.get(i).size(), actual.get(i).size());
			for (int j = 0; j < expected.get(i).size(); j++) {
				assertEquals(expected.get(i).get(j).length, actual.get(i).get(j).length);
				for (int k = 0; k < expected.get(i).get(j).length; k++) {
					assertEquals(expected.get(i).get(j)[k], actual.get(i).get(j)[k]);
				}
			}
		}
	}

}
