<?xml version="1.0" encoding="UTF-8"?>
<!--Transform xdoc files to text.

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
        <xsl:apply-templates/>
    </xsl:template>
    <xsl:template match="section">
        <xsl:value-of select="$newline" /><xsl:text />
        <xsl:number count="section" level="single" format="1.0. "/>
        <xsl:value-of select="@name"/><xsl:text />
        <xsl:value-of select="$newline" /><xsl:text />
        <xsl:apply-templates/>
        <xsl:value-of select="$newline" /><xsl:text />
    </xsl:template>
    <xsl:template match="subsection">
        <xsl:value-of select="$newline" /><xsl:text />
        <xsl:number count="section|subsection" level="multiple" format="1.1. "/>
        <xsl:value-of select="@name"/><xsl:text />
        <xsl:value-of select="$newline" /><xsl:text />
        <xsl:apply-templates/>
        <xsl:value-of select="$newline" /><xsl:text />
    </xsl:template>
    <xsl:template match="release">
        <xsl:value-of select="$newline" /><xsl:text />
        <xsl:value-of select="@version"/><xsl:text />
        <xsl:value-of select="$space" /><xsl:text />
        <xsl:value-of select="@date"/><xsl:text />
        <xsl:value-of select="$newline" /><xsl:text />
        <xsl:apply-templates/>
        <xsl:value-of select="$newline" /><xsl:text />
    </xsl:template>
    <xsl:template match="action">
        <xsl:value-of select="$newline" /><xsl:text />
        <xsl:value-of select="$quot" /><xsl:text />
        <xsl:apply-templates/>
        <xsl:value-of select="$quot" /><xsl:text />
        <xsl:value-of select="$space" /><xsl:text />
        <xsl:value-of select="@type"/><xsl:text />
        <xsl:value-of select="$space" /><xsl:text />
        <xsl:value-of select="@dev"/><xsl:text />
        <xsl:value-of select="$space" /><xsl:text />
        <xsl:value-of select="$newline" /><xsl:text />
    </xsl:template>
    <xsl:template match="a">
        <xsl:value-of select="normalize-space(.)"/><xsl:text />
        <xsl:value-of select="$space" /><xsl:text />
        <xsl:value-of select="$lt" /><xsl:text />
        <xsl:value-of select="@href"/><xsl:text />
        <xsl:value-of select="$gt" /><xsl:text />
    </xsl:template>
    <xsl:template match="p">
        <xsl:apply-templates />
        <xsl:value-of select="$space" /><xsl:text />
    </xsl:template>
    <xsl:template match="img"> &lt;<xsl:value-of select="@src"/>&gt;
        <xsl:apply-templates/>
    </xsl:template>
    <xsl:template match="text()" >
        <xsl:value-of select="normalize-space(.)" /><xsl:text />
    </xsl:template>
</xsl:stylesheet>
