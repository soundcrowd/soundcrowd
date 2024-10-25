package com.tiefensuche.soundcrowd.playback;

import static java.lang.Math.min;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.audio.TeeAudioProcessor;
import androidx.media3.extractor.WavUtil;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class RecordAudioBufferSink implements TeeAudioProcessor.AudioBufferSink {

    private static final String TAG = "WaveFileAudioBufferSink";

    private static final int FILE_SIZE_MINUS_8_OFFSET = 4;
    private static final int FILE_SIZE_MINUS_44_OFFSET = 40;
    private static final int HEADER_LENGTH = 44;

    private final String outputFileName;
    private final byte[] scratchBuffer;
    private final ByteBuffer scratchByteBuffer;

    private int sampleRateHz;
    private int channelCount;
    private @C.PcmEncoding int encoding;
    @Nullable
    private RandomAccessFile randomAccessFile;
    private int bytesWritten;

    private boolean isRecording = false;

    /**
     * Creates a new audio buffer sink that writes to .wav files with the given prefix.
     *
     * @param outputFileName The output filename.
     */
    public RecordAudioBufferSink(String outputFileName) {
        this.outputFileName = outputFileName;
        scratchBuffer = new byte[1024];
        scratchByteBuffer = ByteBuffer.wrap(scratchBuffer).order(ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public void flush(int sampleRateHz, int channelCount, @C.PcmEncoding int encoding) {
        try {
            reset();
        } catch (IOException e) {
            Log.e(TAG, "Error resetting", e);
        }
        this.sampleRateHz = sampleRateHz;
        this.channelCount = channelCount;
        this.encoding = encoding;
    }

    @Override
    public void handleBuffer(ByteBuffer buffer) {
        if (!isRecording)
            return;
        try {
            maybePrepareFile();
            writeBuffer(buffer);
        } catch (IOException e) {
            Log.e(TAG, "Error writing data", e);
        }
    }

    private void maybePrepareFile() throws IOException {
        if (randomAccessFile != null) {
            return;
        }
        RandomAccessFile randomAccessFile = new RandomAccessFile(outputFileName, "rw");
        writeFileHeader(randomAccessFile);
        this.randomAccessFile = randomAccessFile;
        bytesWritten = HEADER_LENGTH;
    }

    private void writeFileHeader(RandomAccessFile randomAccessFile) throws IOException {
        // Write the start of the header as big endian data.
        randomAccessFile.writeInt(WavUtil.RIFF_FOURCC);
        randomAccessFile.writeInt(-1);
        randomAccessFile.writeInt(WavUtil.WAVE_FOURCC);
        randomAccessFile.writeInt(WavUtil.FMT_FOURCC);

        // Write the rest of the header as little endian data.
        scratchByteBuffer.clear();
        scratchByteBuffer.putInt(16);
        scratchByteBuffer.putShort((short) WavUtil.getTypeForPcmEncoding(encoding));
        scratchByteBuffer.putShort((short) channelCount);
        scratchByteBuffer.putInt(sampleRateHz);
        int bytesPerSample = Util.getPcmFrameSize(encoding, channelCount);
        scratchByteBuffer.putInt(bytesPerSample * sampleRateHz);
        scratchByteBuffer.putShort((short) bytesPerSample);
        scratchByteBuffer.putShort((short) (8 * bytesPerSample / channelCount));
        randomAccessFile.write(scratchBuffer, 0, scratchByteBuffer.position());

        // Write the start of the data chunk as big endian data.
        randomAccessFile.writeInt(WavUtil.DATA_FOURCC);
        randomAccessFile.writeInt(-1);
    }

    private void writeBuffer(ByteBuffer buffer) throws IOException {
        RandomAccessFile randomAccessFile = Assertions.checkNotNull(this.randomAccessFile);
        while (buffer.hasRemaining()) {
            int bytesToWrite = min(buffer.remaining(), scratchBuffer.length);
            buffer.get(scratchBuffer, 0, bytesToWrite);
            randomAccessFile.write(scratchBuffer, 0, bytesToWrite);
            bytesWritten += bytesToWrite;
        }
    }

    private void reset() throws IOException {
        @Nullable RandomAccessFile randomAccessFile = this.randomAccessFile;
        if (randomAccessFile == null) {
            return;
        }

        try {
            scratchByteBuffer.clear();
            scratchByteBuffer.putInt(bytesWritten - 8);
            randomAccessFile.seek(FILE_SIZE_MINUS_8_OFFSET);
            randomAccessFile.write(scratchBuffer, 0, 4);

            scratchByteBuffer.clear();
            scratchByteBuffer.putInt(bytesWritten - 44);
            randomAccessFile.seek(FILE_SIZE_MINUS_44_OFFSET);
            randomAccessFile.write(scratchBuffer, 0, 4);
        } catch (IOException e) {
            // The file may still be playable, so just log a warning.
            Log.w(TAG, "Error updating file size", e);
        }

        try {
            randomAccessFile.close();
        } finally {
            this.randomAccessFile = null;
        }
    }

    void setRecord(boolean value) {
        isRecording = value;
    }
}