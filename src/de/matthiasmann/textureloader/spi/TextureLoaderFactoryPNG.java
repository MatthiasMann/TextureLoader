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
package de.matthiasmann.textureloader.spi;

import de.matthiasmann.textureloader.TextureLoader;
import de.matthiasmann.textureloader.TextureLoaderFactory;
import de.matthiasmann.twl.utils.PNGDecoder;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.net.URL;

/**
 * A TextureLoaderFactory for {@link TextureLoaderPNG}
 * 
 * <p>This loader depends on the external {@code PNGDecoder.jar} or {@code TWL.jar}
 * and it's {@link de.matthiasmann.twl.utils.PNGDecoder} class.</p>
 * 
 * @author Matthias Mann
 */
public class TextureLoaderFactoryPNG implements TextureLoaderFactory {

    @Override
    public String getDescription() {
        return "PNG Texture Loader based on de.matthiasmann.twl.utils.PNGDecoder";
    }

    @Override
    public String[] getSupportedExtension() {
        return new String[] { ".png" };
    }

    @Override
    public boolean isAvailable() {
        try {
            return Modifier.isPublic(PNGDecoder.class.getConstructor(InputStream.class).getModifiers());
        } catch(Exception ex) {
            return false;
        }
    }

    @Override
    public TextureLoader createTextureLoader(URL url) {
        return new TextureLoaderPNG(url);
    }
    
}
