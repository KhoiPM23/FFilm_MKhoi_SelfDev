package com.example.project.service;

import com.example.project.config.VnPayConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class VnPayService {

    // Tiêm giá trị HashSecret từ application.properties vào trường secretKey tĩnh của Config
    public VnPayService(
        @Value("${vnpay.vnp_PayUrl:}") String vnp_PayUrl,
        @Value("${vnpay.vnp_TmnCode:}") String vnp_TmnCode,
        @Value("${vnpay.vnp_HashSecret:}") String vnp_HashSecret) {
        
        // Gán vào các trường tĩnh trong VnPayConfig
        VnPayConfig.vnp_PayUrl = vnp_PayUrl;
        VnPayConfig.vnp_TmnCode = vnp_TmnCode;
        VnPayConfig.secretKey = vnp_HashSecret; 
    }

    /**
     * Tạo URL thanh toán VNPay.
     */
    public String createPaymentUrl(long amount, String vnp_TxnRef, String userIp, String description, String appBaseUrl) throws UnsupportedEncodingException {
        
        if (VnPayConfig.vnp_TmnCode.isEmpty() || VnPayConfig.secretKey.isEmpty() || VnPayConfig.vnp_PayUrl.isEmpty()) {
             throw new RuntimeException("VNPay API chưa được cấu hình.");
        }
        
        String vnp_Version = "2.1.0";
        String vnp_Command = "pay";
        String vnp_OrderInfo = description;
        String orderType = "other";
        String vnp_IpAddr = userIp;
        String vnp_TmnCode = VnPayConfig.vnp_TmnCode;

        // Thời gian
        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnp_CreateDate = formatter.format(cld.getTime());
        
        cld.add(Calendar.MINUTE, 15);
        String vnp_ExpireDate = formatter.format(cld.getTime());
        
        // URL trả về
        String vnp_ReturnUrl = appBaseUrl + "/payment/vnpay_return";

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", vnp_Version);
        vnp_Params.put("vnp_Command", vnp_Command);
        vnp_Params.put("vnp_TmnCode", vnp_TmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(amount * 100)); // Nhân 100 là ĐÚNG
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
        vnp_Params.put("vnp_OrderInfo", vnp_OrderInfo);
        vnp_Params.put("vnp_OrderType", orderType);
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", vnp_ReturnUrl);
        vnp_Params.put("vnp_IpAddr", vnp_IpAddr);
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);
        vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);
        
        // Xây dựng chuỗi query string đã encode và sắp xếp
        List fieldNames = new ArrayList(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator itr = fieldNames.iterator();
        
        while (itr.hasNext()) {
            String fieldName = (String) itr.next();
            String fieldValue = (String) vnp_Params.get(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                // Build hash data
                hashData.append(fieldName);
                hashData.append('=');
                hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                
                // Build query
                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()));
                query.append('=');
                query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                
                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }
        
        String queryUrl = query.toString();
        
        // Tạo chữ ký từ chuỗi hashData (đã bao gồm encode value)
        String vnp_SecureHash = VnPayConfig.hmacSHA512(VnPayConfig.secretKey, hashData.toString());
        
        // Nối chữ ký vào URL cuối cùng
        queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;
        
        return VnPayConfig.vnp_PayUrl + "?" + queryUrl;
    }
    
    /**
     * Xác thực phản hồi (Callback) từ VNPay. (Logic đã đúng)
     */
    public boolean verifyVnPayCallback(HttpServletRequest request) {
        Map<String, String> fields = new HashMap<>();
        Enumeration<String> params = request.getParameterNames();
        
        while (params.hasMoreElements()) {
            String fieldName = params.nextElement();
            String fieldValue = request.getParameter(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                fields.put(fieldName, fieldValue);
            }
        }

        String vnp_SecureHash = request.getParameter("vnp_SecureHash");
        if (vnp_SecureHash == null) return false;
        
        // Loại bỏ hash khỏi map để tính lại hash
        if (fields.containsKey("vnp_SecureHashType")) fields.remove("vnp_SecureHashType");
        if (fields.containsKey("vnp_SecureHash")) fields.remove("vnp_SecureHash");
        
        // TÍNH LẠI HASH
        String calculatedHash = VnPayConfig.hashAllFields(fields);
        
        // SO SÁNH HASH
        return calculatedHash != null && calculatedHash.equalsIgnoreCase(vnp_SecureHash);
    }
}