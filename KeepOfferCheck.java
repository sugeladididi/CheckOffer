import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import util.DaoUtil;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

public class KeepOfferCheck {

    /**
     * 查询信息的网址
     */
    private final static String url = "https://app.mokahr.com/api/deliver-query";

    private static Map<String, String> map = new HashMap<String, String>();

    /**
     * 向指定url发送json数据
     *
     * @param url
     * @param param
     * @return
     */
    public static String doPostJSON(String url, JSONObject param) {
        HttpPost httpPost = null;
        String result = null;
        try {
            HttpClient client = new DefaultHttpClient();
            httpPost = new HttpPost(url);
            if (param != null) {
                StringEntity se = new StringEntity(param.toString(), "utf-8");
                httpPost.setEntity(se); // post方法中，加入json数据
                httpPost.setHeader("Content-Type", "application/json");
            }

            HttpResponse response = client.execute(httpPost);
            if (response != null) {
                HttpEntity resEntity = response.getEntity();
                if (resEntity != null) {
                    result = EntityUtils.toString(resEntity, "utf-8");
                }
            }

        } catch (Exception ex) {
//            logger.error("", ex);
        }
        return result;
    }

    /**
     * 向公司简称发起post查询
     *
     * 发送的数据格式形如：
     * {"orgId":"gotokeep","phone":"159********","name":"李**"}
     *
     * @param str
     * @return
     */
    public static String doPostStr(String str) {
        String param = "{\"orgId\":\"" + str + "\",\"phone\":\"159********\",\"name\":\"李**\"}";
        return doPostJSON(url, JSON.parseObject(param));
    }

    /**
     * 发送邮件
     * @param content
     */
    public static void sendMail(String title, String content) throws MessagingException {
        Properties properties = new Properties();
        properties.put("mail.transport.protocol", "smtp");// 连接协议
        properties.put("mail.smtp.host", "smtp.qq.com");// 主机名
        properties.put("mail.smtp.port", "587");// 端口号
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.ssl.enable", "true");// 设置是否使用ssl安全连接 ---一般都使用
        properties.put("mail.debug", "true");// 设置是否显示debug信息 true 会在控制台显示相关信息
        // 得到回话对象
        Session session = Session.getInstance(properties);
        // 获取邮件对象
        Message message = new MimeMessage(session);
        // 设置发件人邮箱地址
        message.setFrom(new InternetAddress("11359713@qq.com"));
        // 设置收件人邮箱地址
//        message.setRecipients(Message.RecipientType.TO, new InternetAddress[]{new InternetAddress("11359713@qq.com"),new InternetAddress("xxx@qq.com"),new InternetAddress("xxx@qq.com")});
        message.setRecipient(Message.RecipientType.TO, new InternetAddress("11359713@qq.com"));//一个收件人
        // 设置邮件标题
        message.setSubject(title);
        // 设置邮件内容
        message.setText(content);
        // 得到邮差对象
        Transport transport = session.getTransport();
        // 连接自己的邮箱账户
        transport.connect("11359713@qq.com", "*************");// 密码为QQ邮箱开通的stmp服务后得到的客户端授权码
        // 发送邮件
        transport.sendMessage(message, message.getAllRecipients());
        transport.close();
    }

    public static void main(String[] args) {

        while (true) {

            // 向数据库更新数据
            Connection conn = DaoUtil.getConnection();
            Statement statement = null;
            ResultSet rs = null;
            try {
                statement = conn.createStatement();
                String sql = "select orgId from offers";
                rs = statement.executeQuery(sql);
                List<Map<String, Object>> list = new ArrayList();
                while (rs.next()) {
                    String orgId = rs.getString("orgId");
                    if (!map.containsKey(orgId))
                        map.put(orgId, "空");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                if (rs != null) {
                    try {
                        rs.close();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }

            // 查询招聘信息
            StringBuilder stringBuilder = new StringBuilder();
            for (Map.Entry<String, String> entry : map.entrySet()) {
                String key = entry.getKey();    // 公司名
                String oldValue = entry.getValue(); // 已存在状态
                try {
                    String jsonValue = doPostStr(key);
                    Map jsonMap = JSON.parseObject(jsonValue, Map.class);
                    JSONArray jsonArray = (JSONArray) jsonMap.get("applications");
                    JSONObject jsonObject = (JSONObject) jsonArray.get(0);
                    Map applicationsMap = jsonObject.toJavaObject(Map.class);
                    String newValue = (String) applicationsMap.get("stage");
                    if (!oldValue.equals(newValue)) {
                        String data = "【" + key + "】由:" + oldValue + "，更新为:" + newValue;
                        System.out.println(data);
                        stringBuilder.append(data + "\n");
                        map.put(key, newValue);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // 发送邮件
            if(stringBuilder.length() > 0){
                try {
                    sendMail("您投递的简历有了新的动态啦！", stringBuilder.toString());
                } catch (MessagingException e) {
                    e.printStackTrace();
                }
            }


            // 停十分钟
            try {
                Thread.sleep(1000 * 60 * 10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    }
}
