package ru.qatools.selenoud.util

import groovy.transform.CompileStatic
import io.netty.buffer.ByteBuf

import static io.netty.buffer.Unpooled.wrappedBuffer

/**
 * @author Ilya Sadykov
 */
@CompileStatic
class InputStreamIterable implements Iterable<ByteBuf> {
    final ByteBufIterator iterator

    InputStreamIterable(InputStream is) {
        this.iterator = new ByteBufIterator(is)
    }

    @Override
    Iterator<ByteBuf> iterator() {
        iterator
    }

    class ByteBufIterator implements Iterator<ByteBuf> {
        static final int CHUNK_SIZE = 2048
        int totalRead = 0
        final InputStream is

        ByteBufIterator(InputStream is) {
            this.is = is
        }

        @Override
        boolean hasNext() {
            is.available() > 0
        }

        @Override
        ByteBuf next() {
            final size = is.available() < CHUNK_SIZE ? is.available() : CHUNK_SIZE
            byte[] buf = new byte[size]
            is.read(buf, totalRead, buf.length)
            wrappedBuffer(buf)
        }
    }

}
