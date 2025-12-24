package com.astro.service;

import com.astro.model.CelestialPoint;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimbadService {
    
    private static final String API_URL = "http://cdsweb.u-strasbg.fr/cgi-bin/nph-sesame/-ox?";

    public CelestialPoint search(String objectName) {
        try {
            if (objectName == null || objectName.trim().isEmpty()) return null;
            
            String encoded = URLEncoder.encode(objectName.trim(), StandardCharsets.UTF_8.toString());
            URL url = new URL(API_URL + encoded);
            
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000); // 5 seg timeout
            
            if (conn.getResponseCode() != 200) return null;

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder content = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) content.append(inputLine);
            in.close();

            String xml = content.toString();
            
            // Buscar etiquetas XML <jradeg> y <jdedeg>
            Pattern pRa = Pattern.compile("<jradeg>(.*?)</jradeg>");
            Pattern pDec = Pattern.compile("<jdedeg>(.*?)</jdedeg>");
            Matcher mRa = pRa.matcher(xml);
            Matcher mDec = pDec.matcher(xml);

            if (mRa.find() && mDec.find()) {
                double ra = Double.parseDouble(mRa.group(1));
                double dec = Double.parseDouble(mDec.group(1));
                return new CelestialPoint(ra, dec);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
