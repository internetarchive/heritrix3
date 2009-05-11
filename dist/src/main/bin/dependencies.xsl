<?xml version="1.0" encoding="UTF-8"?>
<!--Get the dependencies list from project.xml

    $Id$
 -->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="text" version="1.0" encoding="UTF-8"/>
    <xsl:param name="newline" select="'&#xa;'"/>
    <xsl:param name="gt" select="'&gt;'"/>
    <xsl:param name="lt" select="'&lt;'"/>
    <xsl:param name="space" select="' '"/>
    <xsl:param name="quot" select="'&quot;'"/>
    <xsl:template match="/">
        <xsl:apply-templates select="project/dependencies"/>
    </xsl:template>


    <xsl:template match="dependency">
        <xsl:value-of select="$newline" />
        <xsl:number count="dependency" format="1. " />
        <xsl:apply-templates/>
    </xsl:template>
    <xsl:template match="id">
        <xsl:apply-templates/>
        <xsl:value-of select="$newline" />
    </xsl:template>
    <xsl:template match="url|version|description|license">
        <xsl:value-of select="local-name()" /><xsl:text>: </xsl:text>
        <xsl:apply-templates/>
        <xsl:value-of select="$newline" />
    </xsl:template>
    <xsl:template match="text()" >
        <xsl:value-of select="normalize-space(.)" /><xsl:text />
    </xsl:template>
</xsl:stylesheet>
