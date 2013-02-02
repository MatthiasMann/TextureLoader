/*
 * Copyright (c) 2008-2013, Matthias Mann
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Matthias Mann nor the names of its contributors may
 *       be used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.matthiasmann.textureloader;

import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/**
 * A texture class. All methods need to be called from the GL thread.
 * 
 * <p>To use this class with OpenGL core profile you need to enable core
 * profile mode before creating the first texture.</p>
 * 
 * @author Matthias Mann
 * @see #setUseCoreProfile(boolean) 
 */
public final class Texture {

    public enum Format {
        RGBA(GL11.GL_RGBA, 4),
        BGRA(GL12.GL_BGRA, 4),
        LUMINANCE(GL11.GL_LUMINANCE, 1);

        final int glFormat;
        final int bpp;

        private Format(int glFormat, int bpp) {
            this.glFormat = glFormat;
            this.bpp = bpp;
        }

        public int getGLFormat() {
            if(isUseCoreProfile() && glFormat == GL11.GL_LUMINANCE) {
                return GL11.GL_RED;
            }
            return glFormat;
        }

        public int getBytesPerPixel() {
            return bpp;
        }
    }
    
    public enum MipMapMode {
        NONE,
        /**
         * Will use {@link GL14#GL_GENERATE_MIPMAP}
         */
        GL14,
        /**
         * Will use {@link GL30#glGenerateMipmap(int) }
         */
        GL30
    }

    private static boolean useCoreProfile;
    
    final TextureManager manager;
    final URL url;
    final MipMapMode mipMapMode;
    int id;
    int width;
    int height;
    Format format;
    long lastUsedFrame;

    Texture(TextureManager manager, URL url, MipMapMode mipMapMode) {
        this.manager = manager;
        this.url = url;
        this.mipMapMode = mipMapMode;
    }

    /**
     * Creates an unmanaged texture.
     * The mipMapMode is determined by caling {@link #decideMipMapMode() }
     *
     * @param width the width
     * @param height the height
     * @param format the format of the image data
     */
    public Texture(int width, int height, Format format) {
        this(width, height, format, decideMipMapMode());
    }
    
    /**
     * Creates an unmanaged texture
     *
     * @param width the width
     * @param height the height
     * @param format the format of the image data
     * @param mipMapMode the mipMapMode which should be used 
     */
    public Texture(int width, int height, Format format, MipMapMode mipMapMode) {
        if(format == null) {
            throw new NullPointerException("format");
        }
        if(mipMapMode == null) {
            throw new NullPointerException("mipMapMode");
        }
        this.manager = null;
        this.url = null;
        this.width = width;
        this.height = height;
        this.format = format;
        this.mipMapMode = mipMapMode;

        createTexture();
    }

    /**
     * Returns true when core profile mode is active
     * @return true when core profile mode is active
     * @see #setUseCoreProfile(boolean) 
     */
    public static boolean isUseCoreProfile() {
        return useCoreProfile;
    }

    /**
     * Sets core profile mode
     * 
     * <p>In core profile mode {@link Format#LUMINANCE} is mapped to {@link GL11#GL_RED}
     * and mipMapMode is forced to {@link MipMapMode#GL30}.</p>
     * 
     * @param useCoreProfile true for core profile, false for OpenGL 2.0 or compatibility profile
     */
    public static void setUseCoreProfile(boolean useCoreProfile) {
        Texture.useCoreProfile = useCoreProfile;
    }

    /**
     * Loads an unmanaged texture synchronously
     * @param url the URL to load
     * @return the Texture object or null if the URL is not a supported image file
     * @throws IOException if an IO or decode error happend
     */
    public static Texture loadTexture(URL url) throws IOException {
        TextureLoader textureLoader = TextureManager.createTextureLoader(url);
        if(textureLoader == null) {
            return null;
        }
        if(!textureLoader.open()) {
            return null;
        }
        
        int width = textureLoader.getWidth();
        int height = textureLoader.getHeight();
        
        TextureBuffer textureBuffer = TextureBuffer.create(
            width * height *
            textureLoader.getFormat().getBytesPerPixel());
        try {
            ByteBuffer byteBuffer = textureBuffer.map();
            try {
                textureLoader.decode(byteBuffer);
            } finally {
                textureBuffer.unmap();
            }
            Texture texture = new Texture(width, height, textureLoader.getFormat());
            texture.upload(0, 0, width, height, textureBuffer);
            return texture;
        } finally {
            textureBuffer.dispose();
        }
    }
    
    /**
     * Decides the mipMapMode based on the available GL features.
     * 
     * @return {@link MipMapMode#GL30} when core profile mode is enabled,
     *         {@link MipMapMode#GL30} when OpenGL 3.0 is supported,
     *         {@link MipMapMode#GL14} when OpenGL 1.4 is supported,
     *         {@link MipMapMode#NONE} is no supported method was found.
     * @see #setUseCoreProfile(boolean) 
     */
    public static MipMapMode decideMipMapMode() {
        if(isUseCoreProfile()) {
            return MipMapMode.GL30;
        }
        ContextCapabilities caps = GLContext.getCapabilities();
        if(caps.OpenGL30) {
            return MipMapMode.GL30;
        }
        if(caps.OpenGL14) {
            return MipMapMode.GL14;
        }
        return MipMapMode.NONE;
    }
    
    /**
     * Returns true if this texture object was created via {@link TextureManager#getTexture(java.net.URL) }
     * @return true if this texture object is managed
     */
    public boolean isManaged() {
        return manager != null;
    }

    /**
     * Retrieves the width of an unmanaged texture.
     * @return the width for an unmanaged texture and -1 for a managed texture.
     */
    public int getWidth() {
        return isManaged() ? -1 : width;
    }

    /**
     * Retrieves the height of an unmanaged texture.
     * @return the height for an unmanaged texture and -1 for a managed texture.
     */
    public int getHeight() {
        return isManaged() ? -1 : height;
    }

    /**
     * Returns the format used to upload texture data of an unmanaged texture.
     * @return the format for uploading texture data and null for a managed texture.
     */
    public Format getFormat() {
        return isManaged() ? null : format;
    }
    
    /**
     * Returns true when the texture has been deleted
     * @return true when the texture has been deleted
     * @see #delete() 
     */
    public boolean isDeleted() {
        return isManaged() && id == 0;
    }
    
    /**
     * Uploads texture data.
     * Can only be called on unmanaged textures.
     * 
     * @param x the horizontal start of the area to modify
     * @param y the vertical start of the area to modify
     * @param width the width of the area to modify
     * @param height the height of the area to modify
     * @param bb the direct ByteBuffer containing the texture data - interpreted as unsigned bytes
     * 
     * @throws IllegalStateException when this texture has already been deleted
     * @throws UnsupportedOperationException when this texture is managed
     * @see #isManaged() 
     */
    public void upload(int x, int y, int width, int height, ByteBuffer bb) {
        checkNotManaged();
        bind();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, x, y, width, height, format.getGLFormat(), GL11.GL_UNSIGNED_BYTE, bb);
        generateMipMaps();
    }

    /**
     * Uploads texture data.
     * Can only be called on unmanaged textures.
     * 
     * @param x the horizontal start of the area to modify
     * @param y the vertical start of the area to modify
     * @param width the width of the area to modify
     * @param height the height of the area to modify
     * @param ib the direct IntBuffer containing the texture data - interpreted as unsigned bytes
     * 
     * @throws IllegalStateException when this texture has already been deleted
     * @throws UnsupportedOperationException when this texture is managed
     * @see #isManaged() 
     */
    public void upload(int x, int y, int width, int height, IntBuffer ib) {
        checkNotManaged();
        bind();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, x, y, width, height, format.getGLFormat(), GL11.GL_UNSIGNED_BYTE, ib);
        generateMipMaps();
    }

    /**
     * Uploads texture data.
     * Can only be called on unmanaged textures.
     * 
     * 
     * @param x the horizontal start of the area to modify
     * @param y the vertical start of the area to modify
     * @param width the width of the area to modify
     * @param height the height of the area to modify
     * @param buf the TextureBuffer containing the texture data - interpreted as unsigned bytes
     * 
     * @throws IllegalStateException when this texture has already been deleted
     * @throws UnsupportedOperationException when this texture is managed
     * @see #isManaged() 
     */
    public void upload(int x, int y, int width, int height, TextureBuffer buf) {
        checkNotManaged();
        uploadInt(x, y, width, height, buf);
    }
    
    /**
     * Deletes this texture.
     * Can only be called on unmanaged textures.
     * 
     * @throws IllegalStateException when this texture has already been deleted
     * @throws UnsupportedOperationException when this texture is managed
     */
    public void delete() {
        checkNotManaged();
        checkNotDeleted();
        destroy();
    }
    
    private void checkNotManaged() {
        if(isManaged()) {
            throw new UnsupportedOperationException("Can't modify managed textures");
        }
    }
    
    private void checkNotDeleted() {
        if(id == 0) {
            throw new IllegalStateException("Texture was deleted");
        }
    }
    
    void uploadInt(int x, int y, int width, int height, TextureBuffer buf) {
        bind();
        if(buf instanceof TextureBuffer.TextureBufferPBO) {
            TextureBuffer.TextureBufferPBO pbo = (TextureBuffer.TextureBufferPBO)buf;
            assert !pbo.isMapped();
            pbo.bind();
            try {
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, x, y, width, height, format.getGLFormat(), GL11.GL_UNSIGNED_BYTE, 0);
            } finally {
                pbo.unbind();
            }
        } else if(buf instanceof TextureBuffer.TextureBufferPool) {
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, x, y, width, height, format.getGLFormat(), GL11.GL_UNSIGNED_BYTE, buf.map());
        } else {
            throw new IllegalArgumentException("Unsupported TextureBuffer type");
        }
        generateMipMaps();
    }
    
    void generateMipMaps() {
        if(mipMapMode == MipMapMode.GL30) {
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        }
    }

    /**
     * Binds the texture for rendering. If the texture is managed and not yet loaded
     * then it's loading will be started asynchronously.
     * 
     * @throws IllegalStateException when this texture has already been deleted
     */
    public void bind() {
        if(manager != null) {
            lastUsedFrame = manager.currentFrame;
            if(id == 0) {
                manager.loadTexture(this);
            }
        } else {
            checkNotDeleted();
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
    }

    void createTexture() {
        id = GL11.glGenTextures();
        bind();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, format.getGLFormat(), GL11.GL_UNSIGNED_BYTE, (ByteBuffer)null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        switch(mipMapMode) {
            case NONE:
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                break;
            case GL14:
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_GENERATE_MIPMAP, GL11.GL_TRUE);
                break;
            case GL30:
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
                // mip map generation is deferred to after texture upload
                break;
            default:
                throw new AssertionError("Unimplemented MipMapMode: " + mipMapMode);
        }
    }

    void destroy() {
        GL11.glDeleteTextures(id);
        id = 0;
        width = 0;
        height = 0;
    }
}
