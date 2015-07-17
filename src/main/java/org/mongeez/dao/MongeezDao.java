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

package org.mongeez.dao;

import com.github.nlloyd.hornofmongo.MongoRuntime;
import com.github.nlloyd.hornofmongo.MongoScope;
import com.github.nlloyd.hornofmongo.action.MongoScriptAction;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.MongoException;
import com.mongodb.QueryBuilder;
import com.mongodb.WriteConcern;
import org.apache.commons.lang3.time.DateFormatUtils;

import org.mongeez.MongoAuth;
import org.mongeez.commands.ChangeSet;
import org.mozilla.javascript.RhinoException;

import java.util.ArrayList;
import java.util.List;

public class MongeezDao {
    private MongoScope mongoScope;
    private DB db;
    private List<ChangeSetAttribute> changeSetAttributes;

    public MongeezDao(Mongo mongo, String databaseName) {
        this(mongo, databaseName, null);
    }

    public MongeezDao(Mongo mongo, String databaseName, MongoAuth auth) {
        mongoScope = MongoRuntime.createMongoScope();

        if (auth != null){
            if(auth.getAuthDb() == null || auth.getAuthDb().equals(databaseName)) {
                db = mongo.getDB(databaseName);
                if (!db.authenticate(auth.getUsername(), auth.getPassword().toCharArray())) {
                    throw new IllegalArgumentException("Failed to authenticate to database [" + databaseName + "]");
                }
                final String script = String.format("db = connect('%s:%s/%s','%s','%s')",
                                                    mongo.getAddress().getHost(),
                                                    mongo.getAddress().getPort(),
                                                    databaseName,
                                                    auth.getUsername(),
                                                    auth.getPassword());
                MongoRuntime.call(new MongoScriptAction(mongoScope, "connect", script));
            }
            else
            {
                db = mongo.getDB(databaseName);
                DB authDb = mongo.getDB(auth.getAuthDb());
                if (!authDb.authenticate(auth.getUsername(), auth.getPassword().toCharArray())) {
                    throw new IllegalArgumentException("Failed to authenticate to database [" + auth.getAuthDb() + "]");
                }
                final String script = String.format("db = connect('%s:%s/%s','%s','%s')",
                                                    mongo.getAddress().getHost(),
                                                    mongo.getAddress().getPort(),
                                                    auth.getAuthDb(),
                                                    auth.getUsername(),
                                                    auth.getPassword());
                MongoRuntime.call(new MongoScriptAction(mongoScope, "connect", script));
                MongoRuntime.call(new MongoScriptAction(mongoScope, "sibling", String.format("db = db.getSiblingDB('%s')", databaseName)));
            }
        }
        else
        {
            db = mongo.getDB(databaseName);
            final String script = String.format("db = connect('%s:%s/%s')",
                                                mongo.getAddress().getHost(),
                                                mongo.getAddress().getPort(),
                                                databaseName);
            MongoRuntime.call(new MongoScriptAction(mongoScope, "connect", script));
        }
        configure();
    }

    private void configure() {
        addTypeToUntypedRecords();
        loadConfigurationRecord();
        dropObsoleteChangeSetExecutionIndices();
        ensureChangeSetExecutionIndex();
    }

    private void addTypeToUntypedRecords() {
        DBObject q = new QueryBuilder().put("type").exists(false).get();
        BasicDBObject o = new BasicDBObject("$set", new BasicDBObject("type", RecordType.changeSetExecution.name()));
        getMongeezCollection().update(q, o, false, true, WriteConcern.SAFE);
    }

    private void loadConfigurationRecord() {
        DBObject q = new QueryBuilder().put("type").is(RecordType.configuration.name()).get();
        DBObject configRecord = getMongeezCollection().findOne(q);
        if (configRecord == null) {
            if (getMongeezCollection().count() > 0L) {
                // We have pre-existing records, so don't assume that they support the latest features
                configRecord =
                        new BasicDBObject()
                                .append("type", RecordType.configuration.name())
                                .append("supportResourcePath", false);
            } else {
                configRecord =
                        new BasicDBObject()
                                .append("type", RecordType.configuration.name())
                                .append("supportResourcePath", true);
            }
            getMongeezCollection().insert(configRecord, WriteConcern.SAFE);
        }
        Object supportResourcePath = configRecord.get("supportResourcePath");

        changeSetAttributes = new ArrayList<ChangeSetAttribute>();
        changeSetAttributes.add(ChangeSetAttribute.file);
        changeSetAttributes.add(ChangeSetAttribute.changeId);
        changeSetAttributes.add(ChangeSetAttribute.author);
        if (Boolean.TRUE.equals(supportResourcePath)) {
            changeSetAttributes.add(ChangeSetAttribute.resourcePath);
        }
    }

    /**
     * Removes indices that were generated by versions before 0.9.3, since they're not supported by MongoDB 2.4+
     */
    private void dropObsoleteChangeSetExecutionIndices() {
        String indexName = "type_changeSetExecution_file_1_changeId_1_author_1_resourcePath_1";
        DBCollection collection = getMongeezCollection();
        for (DBObject dbObject : collection.getIndexInfo()) {
            if (indexName.equals(dbObject.get("name"))) {
                collection.dropIndex(indexName);
            }
        }
    }

    private void ensureChangeSetExecutionIndex() {
        BasicDBObject keys = new BasicDBObject();
        keys.append("type", 1);
        for (ChangeSetAttribute attribute : changeSetAttributes) {
            keys.append(attribute.name(), 1);
        }
        getMongeezCollection().ensureIndex(keys);
    }

    public boolean wasExecuted(ChangeSet changeSet) {
        BasicDBObject query = new BasicDBObject();
        query.append("type", RecordType.changeSetExecution.name());
        for (ChangeSetAttribute attribute : changeSetAttributes) {
            query.append(attribute.name(), attribute.getAttributeValue(changeSet));
        }
        return getMongeezCollection().count(query) > 0;
    }

    private DBCollection getMongeezCollection() {
        return db.getCollection("mongeez");
    }

    public void runScript(String code) {
        try {
            MongoRuntime.call(new MongoScriptAction(mongoScope, "script", code));
            //db.eval(code);
        }catch (RhinoException e){
            throw new MongoException("Failure in script", e);
        }
    }

    public void logChangeSet(ChangeSet changeSet) {
        BasicDBObject object = new BasicDBObject();
        object.append("type", RecordType.changeSetExecution.name());
        for (ChangeSetAttribute attribute : changeSetAttributes) {
            object.append(attribute.name(), attribute.getAttributeValue(changeSet));
        }
        object.append("date", DateFormatUtils.ISO_DATETIME_TIME_ZONE_FORMAT.format(System.currentTimeMillis()));
        getMongeezCollection().insert(object, WriteConcern.SAFE);
    }
}
