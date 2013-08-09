package org.archive.crawler.restlet.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.archive.crawler.restlet.Flash;

@SuppressWarnings("serial")
public class ViewModel extends LinkedHashMap<String,Object> {
    public ViewModel(){
        super();
    }
    public void setFlashes(List<Flash> flashes){
        List<Map<String,Object>> flashList = new ArrayList<Map<String,Object>>();
        this.put("flashes", flashList);
        for(Flash flash: flashes) {
            Map<String, Object> flashModel = new HashMap<String, Object>();
            flashModel.put("kind", flash.getKind());
            flashModel.put("message", flash.getMessage());
            flashList.add(flashModel);
        }
    }
}
