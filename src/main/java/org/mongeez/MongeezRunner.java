/*
 * Copyright 2011 SecondMarket Labs, LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at  http://www.apache.org/licenses/LICENSE-2.0
 *  
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package org.mongeez;

import com.mongodb.MongoClient;
import org.mongeez.commands.CustomMongeezCommand;
import org.mongeez.reader.ChangeSetFileProvider;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Map;

/**
 * @author oleksii
 * @since 5/2/11
 */
public class MongeezRunner implements InitializingBean {
    private boolean executeEnabled = false;
    private MongoClient mongoClient;
    private String dbName;
    private Resource file;
    private String context;

    private ChangeSetFileProvider changeSetFileProvider;
    private Map<String, CustomMongeezCommand> customCommands;

    @Override
    public void afterPropertiesSet() throws Exception {
        if (isExecuteEnabled()) {
            execute();
        }
    }

    public void execute() throws IOException {
        Mongeez mongeez = new Mongeez();
        mongeez.setMongoClient(mongoClient);
        mongeez.setDbName(dbName);
        mongeez.setContext(context);
        if (changeSetFileProvider != null) {
            mongeez.setChangeSetFileProvider(changeSetFileProvider);
        } else {
            mongeez.setFile(file);
        }

        mongeez.setCustomCommands(customCommands);
        mongeez.process();
    }

    public boolean isExecuteEnabled() {
        return executeEnabled;
    }

    public void setExecuteEnabled(boolean executeEnabled) {
        this.executeEnabled = executeEnabled;
    }

    public void setMongoClient(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public void setFile(Resource file) {
        this.file = file;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public void setChangeSetFileProvider(ChangeSetFileProvider changeSetFileProvider) {
        this.changeSetFileProvider = changeSetFileProvider;
    }

    public String getDbName() {
        return dbName;
    }

    public void setCustomCommands(Map<String, CustomMongeezCommand> customCommands) throws BeansException {
        this.customCommands = customCommands;
    }
}
