package com.github.caryyu.acl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.Base64Utils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AtlassianClientMockService {
    private Log log = LogFactory.getLog(this.getClass());
    @Autowired
    private RestTemplate restTemplate;

    private String JIRA_USERNAME = "liang.yu";
    private String JIRA_PASSWORD = "111111aa";
    private String JIRA_SERVER = "http://jira.livenaked.com";

    /**
     * 创建一个Jira Issue接口
     * @param summary 标题 required
     * @param tourId 自定义字段的Tour编号 required
     * @param reporter required
     * @param assignee required
     * @param securityName 权限名
     * @return 返回的JIRA Issue编号
     */
    public long createIssue(String summary,long tourId,String reporter,String assignee,String securityName){
        String projectKey = "ST";
        String issueType = "Task";

        Map metaInfo = fetchMetaInfo();

        Map body = new HashMap();
        Map fieldsItem = new HashMap();
        fieldsItem.put("project", createMap("id", getProjectId(projectKey, metaInfo)));
        fieldsItem.put("summary", summary);
        if(reporter != null){
            fieldsItem.put("reporter",createMap("name",reporter));
        }
        if(assignee != null){
            fieldsItem.put("assignee",createMap("name",assignee));
        }
        fieldsItem.put("issuetype", createMap("id", getIssueTypeId(projectKey, issueType, metaInfo)));
        fieldsItem.put(getCustomField(projectKey,issueType,"Tour Identity",metaInfo), tourId);
        if(securityName != null) {
            fieldsItem.put("security", createMap("id", getSecurityId(projectKey, issueType, securityName, metaInfo)));
        }
        body.put("fields",fieldsItem);

        try {
            String apiName = getFullApi("/rest/api/2/issue");
            ResponseEntity<Map> responseEntity = restTemplate.exchange(apiName,
                    HttpMethod.POST, new HttpEntity<>(body, createHeaders(JIRA_USERNAME, JIRA_PASSWORD)), Map.class);
            Map m = responseEntity.getBody();
            long jiraIssueId = Long.parseLong(m.get("id").toString());
            return jiraIssueId;
        } catch (HttpStatusCodeException e) {
            log.error(e.getResponseBodyAsString());
            throw e;
        }
    }

    /**
     * 查询Issue信息
     * @param jql Jira Query Language
     * @param startAt 分页开始条
     * @param maxResults 分页结束条
     * @return
     */
    public Map search(String jql,int startAt,int maxResults) {
        String apiName = getFullApi("/rest/api/2/search");
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(apiName)
                .queryParam("jql", jql)
                .queryParam("startAt",startAt)
                .queryParam("maxResults",maxResults);
        try {
            ResponseEntity<Map> responseEntity = restTemplate.exchange(builder.build().encode().toUri(),
                    HttpMethod.GET, new HttpEntity<>(createHeaders(JIRA_USERNAME, JIRA_PASSWORD)), Map.class);
            return responseEntity.getBody();
        } catch (HttpStatusCodeException e) {
            log.error(e.getResponseBodyAsString());
            throw e;
        }
    }

    /**
     * 添加一个观察者
     * @param issueId
     * @param watcher 观察者
     * @return
     */
    public boolean addWatcher(String issueId,String watcher) {
        try {
            String apiName = getFullApi(String.format("/rest/api/2/issue/%s/watchers", issueId));
            HttpHeaders headers = createHeaders(JIRA_USERNAME, JIRA_PASSWORD);
            headers.setContentType(MediaType.APPLICATION_JSON);
            String body = "\"" + watcher + "\"";
            restTemplate.exchange(apiName, HttpMethod.POST, new HttpEntity<String>(body,headers),Void.class);
            return true;
        } catch (HttpStatusCodeException e) {
            log.error(e.getResponseBodyAsString());
            throw e;
        }
    }

    /**
     * 移除一个观察者
     * @param issueId
     * @param watcher 观察者
     * @return
     */
    public boolean removeWatcher(String issueId, String watcher) {
        try {
            String apiName = getFullApi(String.format("/rest/api/2/issue/%s/watchers", issueId));
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(apiName)
                    .queryParam("username", watcher);
            restTemplate.exchange(builder.build().encode().toUri(), HttpMethod.DELETE,
                    new HttpEntity<String>(createHeaders(JIRA_USERNAME, JIRA_PASSWORD)),Void.class);
            return true;
        } catch (HttpStatusCodeException e) {
            log.error(e.getResponseBodyAsString());
            throw e;
        }
    }

    /**
     * 把Issue进行指派
     * @param issueId
     * @param assignee 指派人
     * @return
     */
    public boolean assign(String issueId, String assignee) {
        Map body = new HashMap();
        body.put("name",assignee);
        try {
            String apiName = getFullApi(String.format("/rest/api/2/issue/%s/assignee", issueId));
            restTemplate.exchange(apiName,
                    HttpMethod.PUT, new HttpEntity<>(body, createHeaders(JIRA_USERNAME, JIRA_PASSWORD)), Void.class);
            return true;
        } catch (HttpStatusCodeException e) {
            log.error(e.getResponseBodyAsString());
            throw e;
        }
    }

    /**
     * 流程扭转
     * @param issueId
     * @param transitionName 自定义的流程状态名称
     * @return
     */
    public boolean doTransition(String issueId, String transitionName) {
        Map body = new HashMap();
        body.put("transition", createMap("id", getTransitionIdByName(transitionName, fetchTransitions(issueId))));

        try {
            String apiName = getFullApi(String.format("/rest/api/2/issue/%s/transitions", issueId));
            restTemplate.exchange(apiName,
                    HttpMethod.POST, new HttpEntity<>(body, createHeaders(JIRA_USERNAME, JIRA_PASSWORD)), Map.class);
            return true;
        } catch (HttpStatusCodeException e) {
            log.error(e.getResponseBodyAsString());
            throw e;
        }
    }

    public Map getUsersFromGroup(String groupName,boolean includeInactiveUsers,int startAt,int maxResults){
        String apiName = getFullApi("/rest/api/2/group/member");
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(apiName)
                .queryParam("groupname", groupName)
                .queryParam("includeInactiveUsers", includeInactiveUsers)
                .queryParam("startAt", startAt)
                .queryParam("maxResults", maxResults);
        try {
            ResponseEntity<Map> responseEntity = restTemplate.exchange(builder.build().encode().toUri(),
                    HttpMethod.GET, new HttpEntity<>(createHeaders(JIRA_USERNAME, JIRA_PASSWORD)), Map.class);
            return responseEntity.getBody();
        } catch (HttpStatusCodeException e) {
            log.error(e.getResponseBodyAsString());
            throw e;
        }
    }

    /**
     * 获取元数据结构
     * @return
     * @throws HttpStatusCodeException
     */
    private Map fetchMetaInfo() throws HttpStatusCodeException {
        String apiName = getFullApi("/rest/api/2/issue/createmeta");
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(apiName)
                .queryParam("projectKeys", "ST")
                .queryParam("issuetypeNames", "Task")
                .queryParam("expand", "projects.issuetypes.fields");

        try {
            ResponseEntity<Map> responseEntity = restTemplate.exchange(builder.build().encode().toUri(),
                    HttpMethod.GET, new HttpEntity<>(createHeaders(JIRA_USERNAME, JIRA_PASSWORD)), Map.class);
            return responseEntity.getBody();
        } catch (HttpStatusCodeException e) {
            log.error(e.getResponseBodyAsString());
            throw e;
        }
    }

    private Map fetchTransitions(String issueId) {
        String apiName = getFullApi(String.format("/rest/api/2/issue/%s/transitions",issueId));
        try {
            ResponseEntity<Map> responseEntity = restTemplate.exchange(apiName,
                    HttpMethod.GET, new HttpEntity<>(createHeaders(JIRA_USERNAME, JIRA_PASSWORD)), Map.class);
            return responseEntity.getBody();
        } catch (HttpStatusCodeException e) {
            log.error(e.getResponseBodyAsString());
            throw e;
        }
    }

    private Map createMap(String key,String value){
        Map map = new HashMap();
        map.put(key,value);
        return map;
    }

    private String getProjectId(String key, Map metaInfo) {
        Optional<Map> m = ((List) metaInfo.get("projects")).stream().filter(item -> {
            Map itemMap = (Map) item;
            return key.equalsIgnoreCase(itemMap.get("key").toString());
        }).findFirst();
        String id = m.get().get("id").toString();
        return id;
    }

    private String getIssueTypeId(String projectKey,String issueType,Map metaInfo) {
        Optional<Map> m = ((List) metaInfo.get("projects")).stream().filter(item -> {
            Map itemMap = (Map) item;
            return projectKey.equalsIgnoreCase(itemMap.get("key").toString());
        }).findFirst();

        m = ((List)m.get().get("issuetypes")).stream().filter(item -> {
            Map itemMap = (Map) item;
            return issueType.equalsIgnoreCase(itemMap.get("name").toString());
        }).findFirst();

        String id = m.get().get("id").toString();

        return id;
    }

    private String getCustomField(String projectKey,String issueType,String fieldName,Map metaInfo){
        Optional<Map> m = ((List) metaInfo.get("projects")).stream().filter(item -> {
            Map itemMap = (Map) item;
            return projectKey.equalsIgnoreCase(itemMap.get("key").toString());
        }).findFirst();

        m = ((List)m.get().get("issuetypes")).stream().filter(item -> {
            Map itemMap = (Map) item;
            return issueType.equalsIgnoreCase(itemMap.get("name").toString());
        }).findFirst();

        m = Optional.of((Map)m.get().get("fields"));

        for (Object key : m.get().keySet()){
            if(StringUtils.startsWithIgnoreCase(key.toString(),"customfield_")){
                Map tmp = (Map) m.get().get(key);
                if(fieldName.equalsIgnoreCase(tmp.get("name").toString())){
                    return key.toString();
                }
            }
        }

        return null;
    }

    private String getSecurityId(String projectKey,String issueType,String securityName,Map metaInfo){
        Optional<Map> m = ((List) metaInfo.get("projects")).stream().filter(item -> {
            Map itemMap = (Map) item;
            return projectKey.equalsIgnoreCase(itemMap.get("key").toString());
        }).findFirst();

        m = ((List)m.get().get("issuetypes")).stream().filter(item -> {
            Map itemMap = (Map) item;
            return issueType.equalsIgnoreCase(itemMap.get("name").toString());
        }).findFirst();

        m = Optional.of((Map)m.get().get("fields"));

        m = Optional.of((Map)m.get().get("security"));

        m = ((List)m.get().get("allowedValues")).stream().filter(item -> {
            Map itemMap = (Map) item;
            return securityName.equalsIgnoreCase(itemMap.get("name").toString());
        }).findFirst();

        String id = m.isPresent() ?  m.get().get("id").toString() : null;

        return id;
    }

    private String getTransitionIdByName(String name, Map transitionResult) {
        Optional<Map> m = ((List) transitionResult.get("transitions")).stream().filter(item -> {
            Map itemMap = (Map) item;
            return name.equalsIgnoreCase(itemMap.get("name").toString());
        }).findFirst();
        return m.isPresent() ? m.get().get("id").toString() : null;
    }

    private HttpHeaders createHeaders(String username, String password){
        return new HttpHeaders() {{
            String auth = username + ":" + password;
            byte[] encodedAuth = Base64Utils.encode(
                    auth.getBytes(Charset.forName("US-ASCII")) );
            String authHeader = "Basic " + new String( encodedAuth );
            set( "Authorization", authHeader );
        }};
    }

    private String getFullApi(String string){
        return JIRA_SERVER + string;
    }
}
