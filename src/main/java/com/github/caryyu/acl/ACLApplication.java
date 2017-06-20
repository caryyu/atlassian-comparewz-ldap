package com.github.caryyu.acl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.ldap.core.*;
import org.springframework.stereotype.Component;

import javax.naming.NameClassPair;
import javax.naming.NamingException;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.springframework.ldap.query.LdapQueryBuilder.query;

public class ACLApplication {
    public static void main(String[] args) {
        String configLocation = "classpath:spring-context.xml";

        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(configLocation);

        context.registerShutdownHook();

        context.start();

        context.stop();
    }

    @Component
    public static class ApplicationStartedEvent implements ApplicationListener<ContextStartedEvent>{
        @Autowired
        private AtlassianClientMockService atlassianClientMockService;
        @Autowired
        private LdapTemplate ldapTemplate;

        @Override
        public void onApplicationEvent(ContextStartedEvent event) {
            String groupName = "confluence-users";
            boolean includeInactiveUsers = true;
            boolean isLast = false;
            int startAt = 0;
            int maxResults = 50;

            while (!isLast) {
                Map map = atlassianClientMockService.getUsersFromGroup(groupName,
                        includeInactiveUsers, startAt, maxResults);
                List<Map> users = (List) map.get("values");
                isLast = Boolean.parseBoolean(map.get("isLast").toString());
                startAt += maxResults;

                Iterator<Map> iterator = users.iterator();
                while (iterator.hasNext()) {
                    map = iterator.next();
                    String email = map.get("emailAddress").toString();
                    String username = map.get("name").toString();

                    printUsernameNotSameAndEmailSame(email,username);
                }
            }
        }

        /**
         * 用户名不相同&Email相同
         * @param email
         * @param username
         */
        private void printUsernameNotSameAndEmailSame(String email,String username) {
            ExistingCallbackHandler callbackHandler = new ExistingCallbackHandler();

            ldapTemplate.search(query()
                    .where("objectClass")
                    .is("person")
                    .and("sAMAccountName").not().is(username)
                    .and("mail").is(email), callbackHandler);

            if(callbackHandler.isExist()){
                System.out.printf("username:%s,email:%s\n",username,email);
            }
        }
    }

    static class ExistingCallbackHandler implements NameClassPairCallbackHandler {
        private boolean exist;

        @Override
        public void handleNameClassPair(NameClassPair nameClassPair) throws NamingException {
            this.exist = true;
        }

        public boolean isExist() {
            return exist;
        }
    }
}
