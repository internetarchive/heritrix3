/*
 *  This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 *  Licensed to the Internet Archive (IA) by one or more individual 
 *  contributors. 
 *
 *  The IA licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.archive.modules.extractor;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.archive.modules.CrawlURI;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;


/**
 * A last ditch extractor that will look at the raw byte code and try to extract
 * anything that <i>looks</i> like a link.
 *
 * If used, it should always be specified as the last link extractor in the
 * order file.
 * <p>
 * To accomplish this it will scan through the bytecode and try and build up
 * strings of consecutive bytes that all represent characters that are valid
 * in a URL (see #isURLableChar(int) for details).
 * Once it hits the end of such a string (i.e. finds a character that
 * should not be in a URL) it will try to determine if it has found a URL.
 * This is done be seeing if the string is an IP address prefixed with
 * http(s):// or contains a dot followed by a Top Level Domain and end of
 * string or a slash.
 *
 * @author Kristinn Sigurdsson
 */
public class ExtractorUniversal extends ContentExtractor {

    @SuppressWarnings("unused")
    private static final long serialVersionUID = 3L;

    /**
     * How deep to look into files for URI strings, in bytes.
     */
    {
        setMaxSizeToParse(1*1024*1024L); // 1MB
    }
    public long getMaxSizeToParse() {
        return (Long) kp.get("maxSizeToParse");
    }
    public void setMaxSizeToParse(long threshold) {
        kp.put("maxSizeToParse",threshold);
    }

    /**
     * Matches any string that begins with http:// or https:// followed by
     * something that looks like an ip address (four numbers, none longer then
     * 3 chars seperated by 3 dots). Does <b>not</b> ensure that the numbers are
     * each in the range 0-255.
     */
    protected static final Pattern IP_ADDRESS = Pattern.compile(
        "((http://)|(https://))(\\d(\\d)?(\\d)?\\.\\d(\\d)?(\\d)?\\.\\d(\\d)?(\\d)?\\.\\d(\\d)?(\\d)?)");

    /**
     * Matches any string that begins with a TLD (no .) followed by a '/' slash
     * or end of string. If followed by slash then nothing after the slash is
     * of consequence.
     */
    public static final Pattern TLDs = Pattern.compile(
          "(ac(/.*)?)"  // ac  Ascension Island
        + "|(ad(/.*)?)" // ad  Andorra
        + "|(ae(/.*)?)" // ae  United Arab Emirates
        + "|(af(/.*)?)" // af  Afghanistan
        + "|(ag(/.*)?)" // ag  Antigua and Barbuda
        + "|(ai(/.*)?)" // ai  Anguilla
        + "|(al(/.*)?)" // al  Albania
        + "|(am(/.*)?)" // am  Armenia
        + "|(an(/.*)?)" // an  Netherlands Antilles
        + "|(ao(/.*)?)" // ao  Angola
        + "|(aero(/.*)?)" // aero Air-transport industry
        + "|(aq(/.*)?)" // aq  Antarctica
        + "|(ar(/.*)?)" // ar  Argentina
        + "|(as(/.*)?)" // as  American Samoa
        + "|(at(/.*)?)" // at  Austria
        + "|(au(/.*)?)" // au  Australia
        + "|(aw(/.*)?)" // aw  Aruba
        + "|(az(/.*)?)" // az  Azerbaijan
        + "|(ba(/.*)?)" // ba  Bosnia Hercegovina
        + "|(bb(/.*)?)" // bb  Barbados
        + "|(bd(/.*)?)" // bd  Bangladesh
        + "|(be(/.*)?)" // be  Belgium
        + "|(bf(/.*)?)" // bf  Burkina Faso
        + "|(bg(/.*)?)" // bg  Bulgaria
        + "|(bh(/.*)?)" // bh  Bahrain
        + "|(bi(/.*)?)" // bi  Burundi
        + "|(biz(/.*)?)" // biz Businesses
        + "|(bj(/.*)?)" // bj  Benin
        + "|(bm(/.*)?)" // bm  Bermuda
        + "|(bn(/.*)?)" // bn  Brunei Darussalam
        + "|(bo(/.*)?)" // bo  Bolivia
        + "|(br(/.*)?)" // br  Brazil
        + "|(bs(/.*)?)" // bs  Bahamas
        + "|(bt(/.*)?)" // bt  Bhutan
        + "|(bv(/.*)?)" // bv  Bouvet Island
        + "|(bw(/.*)?)" // bw  Botswana
        + "|(by(/.*)?)" // by  Belarus (Byelorussia)
        + "|(bz(/.*)?)" // bz  Belize
        + "|(ca(/.*)?)" // ca  Canada
        + "|(cc(/.*)?)" // cc  Cocos Islands (Keeling)
        + "|(cd(/.*)?)" // cd  Congo, Democratic Republic of the
        + "|(cf(/.*)?)" // cf  Central African Republic
        + "|(cg(/.*)?)" // cg  Congo, Republic of
        + "|(ch(/.*)?)" // ch  Switzerland
        + "|(ci(/.*)?)" // ci  Cote d'Ivoire (Ivory Coast)
        + "|(ck(/.*)?)" // ck  Cook Islands
        + "|(cl(/.*)?)" // cl  Chile
        + "|(cm(/.*)?)" // cm  Cameroon
        + "|(cn(/.*)?)" // cn  China
        + "|(co(/.*)?)" // co  Colombia
        + "|(com(/.*)?)" // com Commercial
        + "|(coop(/.*)?)" // coop Cooperatives
        + "|(cr(/.*)?)" // cr  Costa Rica
        + "|(cs(/.*)?)" // cs  Czechoslovakia
        + "|(cu(/.*)?)" // cu  Cuba
        + "|(cv(/.*)?)" // cv  Cap Verde
        + "|(cx(/.*)?)" // cx  Christmas Island
        + "|(cy(/.*)?)" // cy  Cyprus
        + "|(cz(/.*)?)" // cz  Czech Republic
        + "|(de(/.*)?)" // de  Germany
        + "|(dj(/.*)?)" // dj  Djibouti
        + "|(dk(/.*)?)" // dk  Denmark
        + "|(dm(/.*)?)" // dm  Dominica
        + "|(do(/.*)?)" // do  Dominican Republic
        + "|(dz(/.*)?)" // dz  Algeria
        + "|(ec(/.*)?)" // ec  Ecuador
        + "|(edu(/.*)?)" // edu Educational Institution
        + "|(ee(/.*)?)" // ee  Estonia
        + "|(eg(/.*)?)" // eg  Egypt
        + "|(eh(/.*)?)" // eh  Western Sahara
        + "|(er(/.*)?)" // er  Eritrea
        + "|(es(/.*)?)" // es  Spain
        + "|(et(/.*)?)" // et  Ethiopia
        + "|(fi(/.*)?)" // fi  Finland
        + "|(fj(/.*)?)" // fj  Fiji
        + "|(fk(/.*)?)" // fk  Falkland Islands
        + "|(fm(/.*)?)" // fm  Micronesia, Federal State of
        + "|(fo(/.*)?)" // fo  Faroe Islands
        + "|(fr(/.*)?)" // fr  France
        + "|(ga(/.*)?)" // ga  Gabon
        + "|(gd(/.*)?)" // gd  Grenada
        + "|(ge(/.*)?)" // ge  Georgia
        + "|(gf(/.*)?)" // gf  French Guiana
        + "|(gg(/.*)?)" // gg  Guernsey
        + "|(gh(/.*)?)" // gh  Ghana
        + "|(gi(/.*)?)" // gi  Gibraltar
        + "|(gl(/.*)?)" // gl  Greenland
        + "|(gm(/.*)?)" // gm  Gambia
        + "|(gn(/.*)?)" // gn  Guinea
        + "|(gov(/.*)?)" // gov Government (US)
        + "|(gp(/.*)?)" // gp  Guadeloupe
        + "|(gq(/.*)?)" // gq  Equatorial Guinea
        + "|(gr(/.*)?)" // gr  Greece
        + "|(gs(/.*)?)" // gs  South Georgia and the South Sandwich Islands
        + "|(gt(/.*)?)" // gt  Guatemala
        + "|(gu(/.*)?)" // gu  Guam
        + "|(gw(/.*)?)" // gw  Guinea-Bissau
        + "|(gy(/.*)?)" // gy  Guyana
        + "|(hk(/.*)?)" // hk  Hong Kong
        + "|(hm(/.*)?)" // hm  Heard and McDonald Islands
        + "|(hn(/.*)?)" // hn  Honduras
        + "|(hr(/.*)?)" // hr  Croatia/Hrvatska
        + "|(ht(/.*)?)" // ht  Haiti
        + "|(hu(/.*)?)" // hu  Hungary
        + "|(id(/.*)?)" // id  Indonesia
        + "|(ie(/.*)?)" // ie  Ireland
        + "|(il(/.*)?)" // il  Israel
        + "|(im(/.*)?)" // im  Isle of Man
        + "|(in(/.*)?)" // in  India
        + "|(info(/.*)?)" // info
        + "|(int(/.*)?)" // int Int. Organizations
        + "|(io(/.*)?)" // io  British Indian Ocean Territory
        + "|(iq(/.*)?)" // iq  Iraq
        + "|(ir(/.*)?)" // ir  Iran, Islamic Republic of
        + "|(is(/.*)?)" // is  Iceland
        + "|(it(/.*)?)" // it  Italy
        + "|(je(/.*)?)" // je  Jersey
        + "|(jm(/.*)?)" // jm  Jamaica
        + "|(jo(/.*)?)" // jo  Jordan
        + "|(jp(/.*)?)" // jp  Japan
        + "|(ke(/.*)?)" // ke  Kenya
        + "|(kg(/.*)?)" // kg  Kyrgyzstan
        + "|(kh(/.*)?)" // kh  Cambodia
        + "|(ki(/.*)?)" // ki  Kiribati
        + "|(km(/.*)?)" // km  Comoros
        + "|(kn(/.*)?)" // kn  Saint Kitts and Nevis
        + "|(kp(/.*)?)" // kp  Korea, Democratic People's Republic
        + "|(kr(/.*)?)" // kr  Korea, Republic of
        + "|(kw(/.*)?)" // kw  Kuwait
        + "|(ky(/.*)?)" // ky  Cayman Islands
        + "|(kz(/.*)?)" // kz  Kazakhstan
        + "|(la(/.*)?)" // la  Lao People's Democratic Republic
        + "|(lb(/.*)?)" // lb  Lebanon
        + "|(lc(/.*)?)" // lc  Saint Lucia
        + "|(li(/.*)?)" // li  Liechtenstein
        + "|(lk(/.*)?)" // lk  Sri Lanka
        + "|(lr(/.*)?)" // lr  Liberia
        + "|(ls(/.*)?)" // ls  Lesotho
        + "|(lt(/.*)?)" // lt  Lithuania
        + "|(lu(/.*)?)" // lu  Luxembourg
        + "|(lv(/.*)?)" // lv  Latvia
        + "|(ly(/.*)?)" // ly  Libyan Arab Jamahiriya
        + "|(ma(/.*)?)" // ma  Morocco
        + "|(mc(/.*)?)" // mc  Monaco
        + "|(md(/.*)?)" // md  Moldova, Republic of
        + "|(mg(/.*)?)" // mg  Madagascar
        + "|(mh(/.*)?)" // mh  Marshall Islands
        + "|(mil(/.*)?)" // mil Military (US Dept of Defense)
        + "|(mk(/.*)?)" // mk  Macedonia, Former Yugoslav Republic
        + "|(ml(/.*)?)" // ml  Mali
        + "|(mm(/.*)?)" // mm  Myanmar
        + "|(mn(/.*)?)" // mn  Mongolia
        + "|(mo(/.*)?)" // mo  Macau
        + "|(mp(/.*)?)" // mp  Northern Mariana Islands
        + "|(mq(/.*)?)" // mq  Martinique
        + "|(mr(/.*)?)" // mr  Mauritani
        + "|(ms(/.*)?)" // ms  Montserrat
        + "|(mt(/.*)?)" // mt  Malta
        + "|(mu(/.*)?)" // mu  Mauritius
        + "|(museum(/.*)?)" // museum Museums
        + "|(mv(/.*)?)" // mv  Maldives
        + "|(mw(/.*)?)" // mw  Malawi
        + "|(mx(/.*)?)" // mx  Mexico
        + "|(my(/.*)?)" // my  Malaysia
        + "|(mz(/.*)?)" // mz  Mozambique
        + "|(na(/.*)?)" // na  Namibia
        + "|(name(/.*)?)" // name Individuals
        + "|(nc(/.*)?)" // nc  New Caledonia
        + "|(ne(/.*)?)" // ne  Niger
        + "|(net(/.*)?)" // net networks
        + "|(nf(/.*)?)" // nf  Norfolk Island
        + "|(ng(/.*)?)" // ng  Nigeria
        + "|(ni(/.*)?)" // ni  Nicaragua
        + "|(nl(/.*)?)" // nl  Netherlands
        + "|(no(/.*)?)" // no  Norway
        + "|(np(/.*)?)" // np  Nepal
        + "|(nr(/.*)?)" // nr  Nauru
        + "|(nt(/.*)?)" // nt  Neutral Zone
        + "|(nu(/.*)?)" // nu  Niue
        + "|(nz(/.*)?)" // nz  New Zealand
        + "|(om(/.*)?)" // om  Oman
        + "|(org(/.*)?)" // org Organization (non-profit)
        + "|(pa(/.*)?)" // pa  Panama
        + "|(pe(/.*)?)" // pe  Peru
        + "|(pf(/.*)?)" // pf  French Polynesia
        + "|(pg(/.*)?)" // pg  Papua New Guinea
        + "|(ph(/.*)?)" // ph  Philippines
        + "|(pk(/.*)?)" // pk  Pakistan
        + "|(pl(/.*)?)" // pl  Poland
        + "|(pm(/.*)?)" // pm  St. Pierre and Miquelon
        + "|(pn(/.*)?)" // pn  Pitcairn Island
        + "|(pr(/.*)?)" // pr  Puerto Rico
        + "|(pro(/.*)?)" // pro Accountants, lawyers, and physicians
        + "|(ps(/.*)?)" // ps  Palestinian Territories
        + "|(pt(/.*)?)" // pt  Portugal
        + "|(pw(/.*)?)" // pw  Palau
        + "|(py(/.*)?)" // py  Paraguay
        + "|(qa(/.*)?)" // qa  Qatar
        + "|(re(/.*)?)" // re  Reunion Island
        + "|(ro(/.*)?)" // ro  Romania
        + "|(ru(/.*)?)" // ru  Russian Federation
        + "|(rw(/.*)?)" // rw  Rwanda
        + "|(sa(/.*)?)" // sa  Saudi Arabia
        + "|(sb(/.*)?)" // sb  Solomon Islands
        + "|(sc(/.*)?)" // sc  Seychelles
        + "|(sd(/.*)?)" // sd  Sudan
        + "|(se(/.*)?)" // se  Sweden
        + "|(sg(/.*)?)" // sg  Singapore
        + "|(sh(/.*)?)" // sh  St. Helena
        + "|(si(/.*)?)" // si  Slovenia
        + "|(sj(/.*)?)" // sj  Svalbard and Jan Mayen Islands
        + "|(sk(/.*)?)" // sk  Slovak Republic
        + "|(sl(/.*)?)" // sl  Sierra Leone
        + "|(sm(/.*)?)" // sm  San Marino
        + "|(sn(/.*)?)" // sn  Senegal
        + "|(so(/.*)?)" // so  Somalia
        + "|(sr(/.*)?)" // sr  Suriname
        + "|(sv(/.*)?)" // sv  El Salvador
        + "|(st(/.*)?)" // st  Sao Tome and Principe
        + "|(sy(/.*)?)" // sy  Syrian Arab Republic
        + "|(sz(/.*)?)" // sz  Swaziland
        + "|(tc(/.*)?)" // tc  Turks and Caicos Islands
        + "|(td(/.*)?)" // td  Chad
        + "|(tf(/.*)?)" // tf  French Southern Territories
        + "|(tg(/.*)?)" // tg  Togo
        + "|(th(/.*)?)" // th  Thailand
        + "|(tj(/.*)?)" // tj  Tajikistan
        + "|(tk(/.*)?)" // tk  Tokelau
        + "|(tm(/.*)?)" // tm  Turkmenistan
        + "|(tn(/.*)?)" // tn  Tunisia
        + "|(to(/.*)?)" // to  Tonga
        + "|(tp(/.*)?)" // tp  East Timor
        + "|(tr(/.*)?)" // tr  Turkey
        + "|(tt(/.*)?)" // tt  Trinidad and Tobago
        + "|(tv(/.*)?)" // tv  Tuvalu
        + "|(tw(/.*)?)" // tw  Taiwan
        + "|(tz(/.*)?)" // tz  Tanzania
        + "|(ua(/.*)?)" // ua  Ukraine
        + "|(ug(/.*)?)" // ug  Uganda
        + "|(uk(/.*)?)" // uk  United Kingdom
        + "|(um(/.*)?)" // um  US Minor Outlying Islands
        + "|(us(/.*)?)" // us  United States
        + "|(uy(/.*)?)" // uy  Uruguay
        + "|(uz(/.*)?)" // uz  Uzbekistan
        + "|(va(/.*)?)" // va  Holy See (City Vatican State)
        + "|(vc(/.*)?)" // vc  Saint Vincent and the Grenadines
        + "|(ve(/.*)?)" // ve  Venezuela
        + "|(vg(/.*)?)" // vg  Virgin Islands (British)
        + "|(vi(/.*)?)" // vi  Virgin Islands (USA)
        + "|(vn(/.*)?)" // vn  Vietnam
        + "|(vu(/.*)?)" // vu  Vanuatu
        + "|(wf(/.*)?)" // wf  Wallis and Futuna Islands
        + "|(ws(/.*)?)" // ws  Western Samoa
        + "|(ye(/.*)?)" // ye  Yemen
        + "|(yt(/.*)?)" // yt  Mayotte
        + "|(yu(/.*)?)" // yu  Yugoslavia
        + "|(za(/.*)?)" // za  South Africa
        + "|(zm(/.*)?)" // zm  Zambia
        + "|(zw(/.*)?)" // zw  Zimbabwe
        );

    /**
     * Constructor.
     */
    public ExtractorUniversal() {
    }

    
    @Override
    protected boolean shouldExtract(CrawlURI uri) {
        return true;
    }
    
    
    @Override
    protected boolean innerExtract(CrawlURI curi) {
        InputStream instream = null;
        try {
            instream = curi.getRecorder().getContentReplayInputStream();
            int ch = instream.read();
            StringBuffer lookat = new StringBuffer();
            long counter = 0;
            long maxdepth = getMaxSizeToParse();
            if(maxdepth<=0) {
                maxdepth = Long.MAX_VALUE;
            }
            long maxURLLength = UURI.MAX_URL_LENGTH;
            boolean foundDot = false;
            while(ch != -1 && ++counter <= maxdepth) {
                if(lookat.length()>maxURLLength){
                    //Exceeded maximum length of a URL. Start fresh.
                    lookat = new StringBuffer();
                    foundDot = false;
                }
                else if(isURLableChar(ch)){
                    //Add to buffer.
                    if(ch == 46){
                        // Current character is a dot '.'
                        foundDot = true;
                    }
                    lookat.append((char)ch);
                } else if(lookat.length() > 3 && foundDot) {
                    // It takes a bare mininum of 4 characters to form a URL
                    // Since we have at least that many let's try link
                    // extraction.
                    String newURL = lookat.toString();
                    if(looksLikeAnURL(newURL))
                    {
                        // Looks like we found something.

                        // Let's start with a little cleanup as we may have
                        // junk in front or at the end.
                        if(newURL.toLowerCase().indexOf("http") > 0){
                            // Got garbage in front of the protocol. Remove.
                            newURL = newURL.substring(newURL.toLowerCase().
                                indexOf("http"));
                        }
                        while(newURL.substring(newURL.length()-1).equals("."))
                        {
                            // URLs can't end with a dot. Strip it off.
                            newURL = newURL.substring(0,newURL.length()-1);
                        }

                        // And add the URL to speculative embeds.
                        numberOfLinksExtracted.incrementAndGet();
                        UURI src = curi.getUURI();
                        UURI dest = UURIFactory.getInstance(newURL);
                        LinkContext lc = LinkContext.SPECULATIVE_MISC;
                        Hop hop = Hop.SPECULATIVE;
                        Link link = new Link(src, dest, lc, hop);
                        curi.getOutLinks().add(link);
                    }
                    // Reset lookat for next string.
                    lookat = new StringBuffer();
                    foundDot = false;
                } else if(lookat.length()>0) {
                    // Didn't get enough chars. Reset lookat for next string.
                    lookat = new StringBuffer();
                    foundDot = false;
                }
                ch = instream.read();
            }
        } catch(IOException e){
            curi.getNonFatalFailures().add(e);
        } finally {
            IOUtils.closeQuietly(instream);
        }
        // Set flag to indicate that link extraction is completed.
        return true;
    }

    /**
     * This method takes a look at a string and determines if it could be a URL.
     * To qualify the string must either begin with "http://" (https would also
     * work) followed by something that looks like an IP address or contain
     * within the string (possible at the end but not at the beginning) a TLD
     * (Top Level Domain) preceded by a dot.
     *
     * @param lookat The string to examine in an effort to determine if it
     * could be a URL
     * @return True if the string matches the above criteria for a URL.
     */
    private boolean looksLikeAnURL(String lookat) {
        if(lookat.indexOf("http://")==0 || lookat.indexOf("https://")==0){
            //Check if the rest of the string looks like an IP address.
            //if so return true. Otherwise continue on.
            Matcher ip = IP_ADDRESS.matcher(lookat);
            boolean testVal = ip.matches();
            if(testVal){
                return true;
            }
        }

        int dot = lookat.indexOf(".");
        if(dot!=0){//An URL can't start with a .tld.
            while(dot != -1 && dot < lookat.length()){
                lookat = lookat.substring(dot+1);
                if (isTLD(lookat.substring(0, lookat.length() <= 6?
                    lookat.length(): 6)))
                {
                    return true;
                }
                dot = lookat.indexOf(".");
            }
        }

        return false;
    }

    /**
     * Checks if a string is equal to known Top Level Domain. The string may
     * contain additional characters <i>after</i> the TLD but not before.
     * @param potentialTLD The string (usually 2-6 chars) to check if it starts
     * with a TLD.
     * @return True if the given string starts with the name of a known TLD
     *
     * @see #TLDs
     */
    private boolean isTLD(String potentialTLD) {
        if(potentialTLD.length()<2){
            return false;
        }

        potentialTLD = potentialTLD.toLowerCase();
        Matcher uri = TLDs.matcher(potentialTLD);
        boolean ret = uri.matches();
        return ret;
    }

    /**
     * Determines if a char (as represented by an int in the range of 0-255) is
     * a character (in the Ansi character set) that can be present in a URL.
     * This method takes a <b>strict</b> approach to what characters can be in
     * a URL.
     * <p>
     * The following are considered to be 'URLable'<br>
     * <ul>
     *  <li> <code># $ % & + , - . /</code> values 35-38,43-47
     *  <li> <code>[0-9]</code> values 48-57
     *  <li> <code>: ; = ? @</code> value 58-59,61,63-64
     *  <li> <code>[A-Z]</code> values 65-90
     *  <li> <code>_</code> value 95
     *  <li> <code>[a-z]</code> values 97-122
     *  <li> <code>~</code> value 126
     * </ul>
     * <p>
     * To summerize, the following ranges are considered URLable:<br>
     * 35-38,43-59,61,63-90,95,97-122,126
     *
     * @param ch The character (represented by an int) to test.
     * @return True if it is a URLable character, false otherwise.
     */
    private boolean isURLableChar(int ch) {
        return (ch>=35 && ch<=38)
            || (ch>=43 && ch<=59)
            || (ch==61)
            || (ch>=63 && ch<=90)
            || (ch==95)
            || (ch>=97 && ch<=122)
            || (ch==126);
    }
}
