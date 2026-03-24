/*
 * Copyright (c) 2026, Vlad Mesco
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.example.audiobookify

import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

data class EpubMetadata(
    val spineList: List<String>,
    val manifestMap: Map<String, String>
)

object EpubMetadataParser {
    fun parse(epubFile: File): EpubMetadata {
        ZipFile(epubFile).use { zip ->
            // 1. Find the OPF file path
            val containerEntry = zip.getEntry("META-INF/container.xml")
                ?: throw IllegalArgumentException("Invalid EPub: META-INF/container.xml missing")

            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()

            val containerDoc = builder.parse(zip.getInputStream(containerEntry))
            val rootfileElement = containerDoc.getElementsByTagName("rootfile").item(0) as Element
            val opfFullPath = rootfileElement.getAttribute("full-path")

            // 2. Parse the OPF file
            val opfEntry = zip.getEntry(opfFullPath)
                ?: throw IllegalArgumentException("Invalid EPub: OPF file not found")

            val opfDoc = builder.parse(zip.getInputStream(opfEntry))

            val basePath = if (opfFullPath.contains("/")) {
                opfFullPath.substringBeforeLast("/") + "/"
            } else {
                ""
            }

            // A. Build Manifest (Mapping IDs to File Paths)
            val manifestMap = mutableMapOf<String, String>()
            val itemNodes = opfDoc.getElementsByTagName("item")
            for (i in 0 until itemNodes.length) {
                val item = itemNodes.item(i) as Element
                manifestMap[item.getAttribute("id")] = basePath + item.getAttribute("href")
            }

            // B. Build Spine (The Reading Order)
            val spineList = mutableListOf<String>()
            val itemrefNodes = opfDoc.getElementsByTagName("itemref")
            for (i in 0 until itemrefNodes.length) {
                val itemref = itemrefNodes.item(i) as Element
                spineList.add(itemref.getAttribute("idref"))
            }

            return EpubMetadata(spineList, manifestMap)
        }
    }
}