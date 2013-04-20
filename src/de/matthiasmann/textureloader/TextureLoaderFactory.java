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
import java.util.Locale;

/**
 *
 * @author Matthias Mann
 */
public interface TextureLoaderFactory {
    
    /**
     * Returns a user readable description of the TextureLoader
     * and the file format it loads.
     * 
     * @return a user readable description
     */
    public String getDescription();
    
    /**
     * Returns an array of lower case file extensions including the leading dot.
     * 
     * <p>For example the JPEG loader should return:</p>
     * {@code return new String[] { ".jpg", ".jpeg" }; }
     * 
     * <p>{@link Locale#ENGLISH} will be used to convert the extension of
     * the resource to lower case before matching with this list.</p>
     * 
     * @return a list of lower case file extensions
     */
    public String[] getSupportedExtensions();
    
    /**
     * Checks if the decoder is usable.
     * 
     * <p>If the decoder depends on external libraries it should only
     * return {@code true} when all required libraries are availble.</p>
     * 
     * @return true if the TextureLoader is read to use
     */
    public boolean isAvailable();
    
    /**
     * Create a {@link TextureLoader} for the specified resource.
     * 
     * @param url the URL of the resource to load
     * @return a TextureLoader instance
     */
    public TextureLoader createTextureLoader(URL url);
}
