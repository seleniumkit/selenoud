package ru.qatools.selenoud.docker

import com.spotify.docker.client.LogStream
import groovy.transform.CompileStatic

/**
 * @author Ilya Sadykov
 */
@CompileStatic
class LogStreamInputStream extends InputStream {
    static final int CHUNK_SIZE = 2048
    int totalRead = 0
    final LogStream ls

    LogStreamInputStream(LogStream ls) {
        this.ls = ls
    }

    @Override
    int read() throws IOException {
        byte[] chunk = new byte[CHUNK_SIZE]
        def bytesRead = read(chunk, totalRead, CHUNK_SIZE)
        totalRead += bytesRead
        bytesRead
    }

    @Override
    int read(byte[] b, int off, int len) throws IOException {
        if (!ls.hasNext()) {
            return -1
        }
        def buffer = ls.next().content()
        def toRead = buffer.position() + len > buffer.capacity() ? buffer.capacity() - buffer.position() : len
        buffer.get(b, buffer.position(), toRead)
        toRead
    }
}
