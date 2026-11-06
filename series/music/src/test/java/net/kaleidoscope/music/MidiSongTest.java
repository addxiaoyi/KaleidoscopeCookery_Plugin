package net.kaleidoscope.music;

import org.junit.jupiter.api.Test;
import javax.sound.midi.*;
import static org.junit.jupiter.api.Assertions.*;

class MidiSongTest {
    @Test void mergesTempoTrackAndNoteTracksInTickOrder() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 480);
        Track tempo = sequence.createTrack();
        tempo.add(new MidiEvent(new MetaMessage(0x51, new byte[]{0x0f, 0x42, 0x40}, 3), 480));
        Track notes = sequence.createTrack();
        note(notes, 0, 60, 127);
        note(notes, 480, 62, 100);
        note(notes, 960, 64, 90);
        MidiSong song = MidiSong.read(sequence);
        assertArrayEquals(new long[]{0, 10, 30}, song.notes().stream().mapToLong(MidiSong.Note::tick).toArray());
    }

    @Test void velocityZeroNoteOnIsNotAnotherNote() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 96);
        Track track = sequence.createTrack();
        note(track, 0, 60, 127);
        note(track, 96, 60, 0);
        assertEquals(1, MidiSong.read(sequence).notes().size());
    }

    @Test void programChangesStayOnTheirOwnChannels() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 96);
        Track track = sequence.createTrack();
        track.add(new MidiEvent(new ShortMessage(ShortMessage.PROGRAM_CHANGE, 0, 24, 0), 0));
        note(track, 1, 60, 127);
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 1, 60, 127), 2));
        var notes = MidiSong.read(sequence).notes();
        assertEquals(24, notes.get(0).program());
        assertEquals(0, notes.get(1).program());
    }

    @Test void supportsSmpteTiming() throws Exception {
        Sequence sequence = new Sequence(Sequence.SMPTE_24, 10);
        note(sequence.createTrack(), 240, 60, 127);
        assertEquals(20, MidiSong.read(sequence).notes().getFirst().tick());
    }

    @Test void rejectsEmptyAndInvalidTempoSongs() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> MidiSong.read(new Sequence(Sequence.PPQ, 96)));
        Sequence sequence = new Sequence(Sequence.PPQ, 96);
        sequence.createTrack().add(new MidiEvent(new MetaMessage(0x51, new byte[3], 3), 0));
        assertThrows(IllegalArgumentException.class, () -> MidiSong.read(sequence));
    }

    @Test void rejectsExcessiveDurationBeforePlaying() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 96);
        note(sequence.createTrack(), Long.MAX_VALUE, 60, 127);
        assertThrows(IllegalArgumentException.class, () -> MidiSong.read(sequence));
    }

    @Test void foldsOctavesIntoTheVanillaPitchRange() {
        for (int key = 0; key < 128; key++) assertTrue(MidiSong.pitch(key) >= 0.5f && MidiSong.pitch(key) <= 2f);
        assertEquals(1f, MidiSong.pitch(66));
        assertThrows(IllegalArgumentException.class, () -> MidiSong.pitch(128));
    }

    private static void note(Track track, long tick, int key, int velocity) throws Exception {
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, key, velocity), tick));
    }
}
