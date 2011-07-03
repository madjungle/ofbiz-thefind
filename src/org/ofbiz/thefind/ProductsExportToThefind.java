/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ofbiz.thefind;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javolution.util.FastList;
import javolution.util.FastMap;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilFormatOut;
import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityOperator;
import org.ofbiz.entity.util.EntityUtil;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ServiceUtil;

import com.csvreader.CsvReader;

public class ProductsExportToThefind {

    private static final String resource = "TheFindUiLabels";
    private static final String module = ProductsExportToThefind.class.getName();

    public static Map<String, Object> exportProductsToTheFind(DispatchContext dctx, Map<String, Object> context) {
        Locale locale = (Locale) context.get("locale");
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        String errMsg = null;
        String productStoreId = (String) context.get("productStoreId");

        if (UtilValidate.isNotEmpty(productStoreId)) {
            GenericValue thefindConfig = null;
            //put feed file to Thefind
            try {
                thefindConfig = delegator.findOne("TheFindConfig", false, UtilMisc.toMap("productStoreId", productStoreId));
                if (UtilValidate.isNotEmpty(thefindConfig)) {
                    String hostname = thefindConfig.getString("hostname");
                    String localFilename = thefindConfig.getString("localFilename");
                    String password = thefindConfig.getString("theFindPassword");
                    String remoteFilename = thefindConfig.getString("remoteFilename");
                    String username = thefindConfig.getString("theFindUsername");
                    
                    Map<String, Object> result = null;
                    //expoort product data to text file
                    result = buildDataItemsCsv(dctx, context, localFilename);
                    
                
                    Map<String, String> inputMap = UtilMisc.toMap("hostname", hostname,
                            "localFilename", localFilename,
                            "password", password,
                            "remoteFilename", remoteFilename,
                            "username", username);
                    Map<String, Object> putfeedfileResult = dispatcher.runSync("ftpPutFile", inputMap);
                    if (!ServiceUtil.isFailure(putfeedfileResult)) {
                        Debug.log("======returning with result: " + result);
                        Debug.log("======returning with putfeedfileResult: " + putfeedfileResult);
                    }
                    return result;
                } else {
                    errMsg = UtilProperties.getMessage(resource, "thefindsearchevents.no_results_found_probably_error_constraints", locale);
                    Debug.logError(errMsg, module);
                }
            } catch (GenericServiceException e) {
                errMsg = UtilProperties.getMessage(resource, "productsExportTothefind.exceptionCallingExportToThefind", locale);
                Debug.logError(e, errMsg, module);
            } catch (GenericEntityException e) {
                Debug.logError("Unable to find value for TheFindConfig", module);
                e.printStackTrace();
            }
        }
        Map<String, Object> result = ServiceUtil.returnSuccess();
        Debug.log("======[exportProductsToTheFind]:returning with result: " + result);
        return result;
    }
    
    public class FileUtil {
    
    public void removeLineFromFile(String file, String lineToRemove) {

	    try {

	      File inFile = new File(file);
	      
	      if (!inFile.isFile()) {
	        System.out.println("Parameter is not an existing file");
	        return;
	      }
	       
	      //Construct the new file that will later be renamed to the original filename.
	      File tempFile = new File(inFile.getAbsolutePath() + ".tmp");
	      
	      BufferedReader br = new BufferedReader(new FileReader(file));
	      PrintWriter pw = new PrintWriter(new FileWriter(tempFile));
	      
	      String line = null;

	      //Read from the original file and write to the new
	      //unless content matches data to be removed.
	      while ((line = br.readLine()) != null) {
	        
	        if (!line.trim().equals(lineToRemove)) {

	          pw.println(line);
	          pw.flush();
	        }
	      }
	      pw.close();
	      br.close();
	      
	      //Delete the original file
	      if (!inFile.delete()) {
	        System.out.println("Could not delete file");
	        return;
	      }
	      
	      //Rename the new file to the filename the original file had.
	      if (!tempFile.renameTo(inFile))
	        System.out.println("Could not rename file");
	      
	    }
	    catch (FileNotFoundException ex) {
	      ex.printStackTrace();
	    }
	    catch (IOException ex) {
	      ex.printStackTrace();
	    }
	  }
    }

    private static Map<String, Object> buildDataItemsCsv(DispatchContext dctx, Map<String, Object> context, String localFilename) {
        Locale locale = (Locale)context.get("locale");
        try {
            Delegator delegator = dctx.getDelegator();
            LocalDispatcher dispatcher = dctx.getDispatcher();
            List<String> selectResult = UtilGenerics.checkList(context.get("selectResult"), String.class);
            String webSiteUrl = (String)context.get("webSiteUrl");
            String imageUrl = (String)context.get("imageUrl");
            String countryCode = (String)context.get("countryCode");
            String webSiteMountPoint = (String)context.get("webSiteMountPoint");
            String productStoreId = (String)context.get("productStoreId");

            if (!webSiteUrl.startsWith("http://") && !webSiteUrl.startsWith("https://")) {
                webSiteUrl = "http://" + webSiteUrl;
            }
            if (webSiteUrl.endsWith("/")) {
                webSiteUrl = webSiteUrl.substring(0, webSiteUrl.length() - 1);
            }
            
            if (UtilValidate.isNotEmpty(imageUrl)) {
                if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
                    imageUrl = "http://" + imageUrl;
                }
                if (imageUrl.endsWith("/")) {
                    imageUrl = imageUrl.substring(0, imageUrl.length() - 1);
                }
            }

            if (webSiteMountPoint.endsWith("/")) {
                webSiteMountPoint = webSiteMountPoint.substring(0, webSiteMountPoint.length() - 1);
            }
            if (webSiteMountPoint.startsWith("/")) {
                webSiteMountPoint = webSiteMountPoint.substring(1, webSiteMountPoint.length());
            }

            String productCurrency = null;
            if ("US".equals(countryCode)) {
                productCurrency = "USD";
            } else if ("GB".equals(countryCode)) {
                productCurrency = "GBP";
            } else if ("DE".equals(countryCode)) {
                productCurrency = "EUR";
            } else {
                Debug.logError("Exception during building data items to thefind, Country Code must be either US, UK or DE: "+countryCode, module);
                return ServiceUtil.returnFailure(UtilProperties.getMessage(resource, "productsExportTothefind.invalidCountryCode", locale));
            }
            // Get the list of products to be exported to TheFind
            List<GenericValue> productsList  = delegator.findList("Product", EntityCondition.makeCondition("productId", EntityOperator.IN, selectResult), null, null, null, false);

            // Iterate the product list getting all the relevant data
            Iterator<GenericValue> productsListItr = productsList.iterator();
            int index = 0;
            //GenericValue googleProduct;
            while (productsListItr.hasNext()) {
                GenericValue prod = productsListItr.next();
                
                //price
                Map<String, Object> prices = null;
                prices = getProductPrice(dispatcher, prod, productCurrency);
                String price = null;
                String onSale = "";
                String salePrice = "";
                if (UtilValidate.isNotEmpty(prices)) {
                    price = (String) prices.get("price").toString();
                    String isSale = (String) prices.get("isSale").toString();
                    if ("true".equals(isSale)) {
                        onSale = "Yes";
                        salePrice = (String) prices.get("salePrice").toString();
                    }
                    if (UtilValidate.isEmpty(price)) {
                        Debug.logInfo("Price not found for product [" + prod.getString("productId")+ "]; product will not be exported.", module);
                        continue;
                    }
                }
                //goodIdentifications
                List<GenericValue> goodIdentifications = delegator.findByAndCache("GoodIdentification", UtilMisc.toMap(
                        "productId", prod.getString("productId")));
                String ean = "";
                String isbn = "";
                String mpn = "";
                if (!UtilValidate.isEmpty(goodIdentifications)) {
                    for (GenericValue goodIdentification : goodIdentifications) {
                        String goodIdentificationTypeId = goodIdentification.getString("goodIdentificationTypeId");
                        String idValue = goodIdentification.getString("idValue");
                        if ("EAN".equals(goodIdentificationTypeId)) {
                            ean = idValue;
                            if (UtilValidate.isEmpty(idValue)) {
                                ean = " ";
                            }
                        } else if ("ISBN".equals(goodIdentificationTypeId)) {
                            isbn = idValue;
                            if (UtilValidate.isEmpty(idValue)) {
                                isbn = " ";
                            }
                        } else if ("MANUFACTURER_ID_NO".equals(goodIdentificationTypeId)) {
                            mpn = idValue;
                            if (UtilValidate.isEmpty(idValue)) {
                                mpn = " ";
                            }
                        }
                    }
                }
                
                // Shipping cost
                String chargeShipping = prod.getString("chargeShipping");
                String freeShipping = "";
                String chargeShippingCost = "";
                if ("Y".equals(chargeShipping)) {
                    //calculate the shipping cost
                    List<GenericValue> shipmentEstimateCosts = delegator.findByAndCache("ShipmentCostEstimate", UtilMisc.toMap("productStoreId", productStoreId,
                            "shipmentMethodTypeId", "STANDARD",
                            "carrierPartyId", "USPS",
                            "carrierRoleTypeId", "CARRIER"));
                    if (!UtilValidate.isEmpty(shipmentEstimateCosts)) {
                        for (GenericValue shipmentEstimateCost : shipmentEstimateCosts) {
                            chargeShippingCost = shipmentEstimateCost.getString("orderFlatPrice");
                            if (UtilValidate.isEmpty(chargeShippingCost)) {
                                chargeShippingCost = "0";
                            }
                        }
                    } else {
                        chargeShippingCost = "0";
                    }
                } else {
                    freeShipping = "Free Shipping";
                }
                if (UtilValidate.isEmpty(chargeShipping)) {
                    freeShipping = "Free Shipping";
                }
                //sku, brand, weight
                String sku = prod.getString("productId");
                //String rating = prod.getString("productRating");
                //String stockQty = prod.getString("quantityIncluded");
                String brandName = "";
                if (UtilValidate.isEmpty(brandName)) {
                    brandName = " ";
                } else {
                    brandName = prod.getString("brandName");
                }
                String weight = "";
                if (UtilValidate.isEmpty(weight)) {
                    weight = " ";
                } else {
                    weight = prod.getString("weight");
                }
              //color
                List<GenericValue> productFeatureAndAppls = delegator.findByAndCache("ProductFeatureAndAppl", UtilMisc.toMap(
                        "productId", prod.getString("productId"),
                        "productFeatureTypeId", "COLOR"));
                List<String> colorlist = FastList.newInstance();
                String colorlists = "";
                if (!UtilValidate.isEmpty(productFeatureAndAppls)) {
                    for (GenericValue productFeatureAndAppl : productFeatureAndAppls) {
                        colorlist.add(productFeatureAndAppl.getString("description"));
                    }
                    colorlists = colorlist.toString();
                    if (colorlists.startsWith("{")) {
                        colorlists = colorlists.replace("{", "\"");
                        colorlists = colorlists.replace("}", "\"");
                    }
                } else {
                    colorlists = " ";
                }
                
                //Condition
                String condition = "new";
                // TODO: improve this (i.e. get the relative path from the properties file)
                String title = UtilFormatOut.encodeXmlValue(prod.getString("productName"));
                if (UtilValidate.isEmpty(title)) {
                    title = UtilFormatOut.encodeXmlValue(prod.getString("internalName"));
                }
                //Get the product url following seo rules
                String link = "";
                GenericValue productContentInfo;
                List<GenericValue> productContentInfos = delegator.findByAndCache("ProductContentAndInfo", UtilMisc.toMap(
                        "productId", prod.getString("productId"),
                        "productContentTypeId", "ALTERNATIVE_URL"));
                productContentInfo = EntityUtil.getFirst(productContentInfos);
                if (productContentInfo != null) {
                    String productUrl = productContentInfo.getString("drObjectInfo");
                    link = webSiteUrl + "/" + productUrl;
                } else {
                    // OFBiz default URL
                    link = webSiteUrl + "/" + webSiteMountPoint + "/control/product/~product_id=" + prod.getString("productId");
                }
                String description = UtilFormatOut.encodeXmlValue(prod.getString("description"));
                if (UtilValidate.isEmpty(description)) {
                    description = UtilFormatOut.encodeXmlValue(prod.getString("internalName"));
                }
                if (UtilValidate.isEmpty(description)) {
                    description = "-";
                }
                //image link
                String imageLink = null;
                if (UtilValidate.isEmpty(imageUrl)) {
                    imageUrl = webSiteUrl;
                }
                if (UtilValidate.isNotEmpty(prod.getString("largeImageUrl"))) {
                    imageLink = imageUrl + prod.getString("largeImageUrl");
                } else if (UtilValidate.isNotEmpty(prod.getString("mediumImageUrl"))) {
                    imageLink = imageUrl + prod.getString("mediumImageUrl");
                } else if (UtilValidate.isNotEmpty(prod.getString("smallImageUrl"))) {
                    imageLink = imageUrl + prod.getString("smallImageUrl");
                } else if (UtilValidate.isNotEmpty(prod.getString("originalImageUrl"))) {
                    imageLink = imageUrl + prod.getString("originalImageUrl");
                }
                //feed file location
                String outputFile = localFilename;
                // before we open the file check to see if it already exists
                boolean alreadyExists = new File(outputFile).exists();
                try {
                    // use FileWriter constructor that specifies open for appending
                    FileWriter csvOutput = new FileWriter(outputFile, true);
                    
                    // if the file didn't already exist then we need to write out the header line
                    if (alreadyExists) {
                        if (UtilValidate.isNotEmpty(price) || UtilValidate.isNotEmpty(imageLink)) {
                            // write out a few records
                            if (UtilValidate.isEmpty(imageLink)) {
                                imageLink = "";
                            }
                            if (UtilValidate.isEmpty(price)) {
                                price = "";
                            }
                            csvOutput.write(title);
                            csvOutput.write("\t");
                            csvOutput.write(description);
                            csvOutput.write("\t");
                            csvOutput.write(imageLink);
                            csvOutput.write("\t");
                            csvOutput.write(link);
                            csvOutput.write("\t");
                            csvOutput.write(price);
                            csvOutput.write("\t");
                            csvOutput.write(onSale);
                            csvOutput.write("\t");
                            csvOutput.write(salePrice);
                            csvOutput.write("\t");
                            csvOutput.write(ean);
                            csvOutput.write("\t");
                            csvOutput.write(mpn);
                            csvOutput.write("\t");
                            csvOutput.write(isbn);
                            csvOutput.write("\t");
                            csvOutput.write(chargeShippingCost);
                            csvOutput.write("\t");
                            csvOutput.write(freeShipping);
                            csvOutput.write("\t");
                            csvOutput.write(sku);
                            csvOutput.write("\t");
                            csvOutput.write(brandName);
                            csvOutput.write("\t");
                            csvOutput.write(weight);
                            csvOutput.write("\t");
                            csvOutput.write(colorlists);
                            csvOutput.write("\t");
                            csvOutput.write(condition);
                            csvOutput.write("\n");
                            
                        }
                        class FileUtil {
                            public void removeLineFromFile(String file, String lineToRemove) {
                                try {
                                  File inFile = new File(file);
                                  if (!inFile.isFile()) {
                                    System.out.println("Parameter is not an existing file");
                                    return;
                                  }

                                  //Construct the new file that will later be renamed to the original filename.
                                  File tempFile = new File(inFile.getAbsolutePath() + ".tmp");
                                  
                                  BufferedReader br = new BufferedReader(new FileReader(file));
                                  PrintWriter pw = new PrintWriter(new FileWriter(tempFile));
                                  
                                  String line = null;

                                  //Read from the original file and write to the new
                                  //unless content matches data to be removed.
                                  while ((line = br.readLine()) != null) {
                                    
                                    if (!line.trim().equals(lineToRemove)) {
                                      pw.println(line);
                                      pw.flush();
                                    }
                                  }
                                  pw.close();
                                  br.close();

                                  //Delete the original file
                                  if (!inFile.delete()) {
                                    System.out.println("Could not delete file");
                                    return;
                                  }

                                  //Rename the new file to the filename the original file had.
                                  if (!tempFile.renameTo(inFile))
                                    System.out.println("Could not rename file");
                                  
                                }
                                catch (FileNotFoundException ex) {
                                  ex.printStackTrace();
                                }
                                catch (IOException ex) {
                                  ex.printStackTrace();
                                }
                              }
                        }
                        //read exists file.
                        CsvReader reader = new CsvReader(outputFile, '\t');
                        if (UtilValidate.isNotEmpty(reader)) {
                            reader.readHeaders();
                            long count = 0;
                            while (reader.readRecord()) {
                                String productId = reader.get(12);
                                if (sku.equals(productId)) {
                                    FileUtil util = new FileUtil();
                                    util.removeLineFromFile(outputFile, sku);
                                    //new write
                                    if (UtilValidate.isNotEmpty(price) || UtilValidate.isNotEmpty(imageLink)) {
                                        // write out a few records
                                        if (UtilValidate.isEmpty(imageLink)) {
                                            imageLink = "";
                                        }
                                        if (UtilValidate.isEmpty(price)) {
                                            price = "";
                                        }
                                        csvOutput.write(title);
                                        csvOutput.write("\t");
                                        csvOutput.write(description);
                                        csvOutput.write("\t");
                                        csvOutput.write(imageLink);
                                        csvOutput.write("\t");
                                        csvOutput.write(link);
                                        csvOutput.write("\t");
                                        csvOutput.write(price);
                                        csvOutput.write("\t");
                                        csvOutput.write(onSale);
                                        csvOutput.write("\t");
                                        csvOutput.write(salePrice);
                                        csvOutput.write("\t");
                                        csvOutput.write(ean);
                                        csvOutput.write("\t");
                                        csvOutput.write(mpn);
                                        csvOutput.write("\t");
                                        csvOutput.write(isbn);
                                        csvOutput.write("\t");
                                        csvOutput.write(chargeShippingCost);
                                        csvOutput.write("\t");
                                        csvOutput.write(freeShipping);
                                        csvOutput.write("\t");
                                        csvOutput.write(sku);
                                        csvOutput.write("\t");
                                        csvOutput.write(brandName);
                                        csvOutput.write("\t");
                                        csvOutput.write(weight);
                                        csvOutput.write("\t");
                                        csvOutput.write(colorlists);
                                        csvOutput.write("\t");
                                        csvOutput.write(condition);
                                        
                                    }
                                } 
                            }
                        }
                    } else {
                        //write header
                        csvOutput.write("Title");
                        csvOutput.write("\t");
                        csvOutput.write("Description");
                        csvOutput.write("\t");
                        csvOutput.write("Image_Link");
                        csvOutput.write("\t");
                        csvOutput.write("Page_URL");
                        csvOutput.write("\t");
                        csvOutput.write("Price");
                        csvOutput.write("\t");
                        csvOutput.write("Sale");
                        csvOutput.write("\t");
                        csvOutput.write("Sale_Price");
                        csvOutput.write("\t");
                        csvOutput.write("UPC-EAN");
                        csvOutput.write("\t");
                        csvOutput.write("MPN");
                        csvOutput.write("\t");
                        csvOutput.write("ISBN");
                        csvOutput.write("\t");
                        csvOutput.write("Shipping_Cost");
                        csvOutput.write("\t");
                        csvOutput.write("Free_Shipping");
                        csvOutput.write("\t");
                        csvOutput.write("Sku");
                        csvOutput.write("\t");
                        csvOutput.write("Brand");
                        csvOutput.write("\t");
                        csvOutput.write("Weight");
                        csvOutput.write("\t");
                        csvOutput.write("Color");
                        csvOutput.write("\t");
                        csvOutput.write("Condition");
                        csvOutput.write("\n");
                    }
                    //csvOutput.flush();
                    csvOutput.close();

                } catch (IOException e) {
                    e.printStackTrace();
                }
                index++;
            }
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError(UtilProperties.getMessage(resource, "thefindExportUnableToReadFromProduct", locale) + e.toString());
        }
        Map<String, Object> result = ServiceUtil.returnSuccess();
        Debug.log("======returning with result: " + result);
        return result;
    }

    private static Map<String, Object> getProductPrice(LocalDispatcher dispatcher, GenericValue product, String productCurrency) {
        Map<String, Object> price = null;
        Map<String, Object> map = FastMap.newInstance();
        try {
            map = dispatcher.runSync("calculateProductPrice", UtilMisc.toMap("product", product, "currencyUomId", productCurrency));
            boolean validPriceFound = ((Boolean)map.get("validPriceFound")).booleanValue();
            if ("true".equals(validPriceFound)) {
                price = UtilMisc.toMap("price", map.get("defaultPrice"),
                        "isSale", map.get("isSale"),
                        "salePrice", map.get("price"));
            }
        } catch (GenericServiceException e1) {
            Debug.logError("calculateProductPrice Service exception getting the product price:" + e1.toString(), module);
        }
        return price;
    }
	
	
}