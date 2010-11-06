package org.archive.modules.canonicalize;

import org.apache.commons.httpclient.URIException;
import org.archive.modules.canonicalize.StripSessionCFIDs;
import org.archive.state.ModuleTestBase;

public class StripSessionCFIDsTest extends ModuleTestBase {

    private static final String [] INPUTS = {
        "http://a.b.c/boo?CFID=1169580&CFTOKEN=48630702" +
            "&dtstamp=22%2F08%2F2006%7C06%3A58%3A11",
        "http://a.b.c/boo?CFID=12412453&CFTOKEN=15501799" +
        "   &dt=19_08_2006_22_39_28",
        "http://a.b.c/boo?CFID=14475712" +
        "   &CFTOKEN=2D89F5AF-3048-2957-DA4EE4B6B13661AB" +
            "&r=468710288378&m=forgotten",
        "http://a.b.c/boo?CFID=16603925" +
        "   &CFTOKEN=2AE13EEE-3048-85B0-56CEDAAB0ACA44B8",
        "http://a.b.c/boo?CFID=4308017&CFTOKEN=63914124" +
            "&requestID=200608200458360%2E39414378"
    };
    
    private static final String [] OUTPUTS = {
        "http://a.b.c/boo?dtstamp=22%2F08%2F2006%7C06%3A58%3A11",
        "http://a.b.c/boo?dt=19_08_2006_22_39_28",
        "http://a.b.c/boo?r=468710288378&m=forgotten",
        "http://a.b.c/boo?",
        "http://a.b.c/boo?requestID=200608200458360%2E39414378"
    };

    public void testCanonicalize() throws URIException {
        for (int i = 0; i < INPUTS.length; i++) {
            String result = (new StripSessionCFIDs().
                canonicalize(INPUTS[i]));
            assertEquals(OUTPUTS[i],result);
        }
    }
}