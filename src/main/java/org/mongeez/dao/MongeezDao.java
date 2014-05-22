package org.mongeez.dao;

import com.mongodb.DB;
import com.mongodb.DBObject;
import org.mongeez.commands.ChangeSet;

/**
 * MongeezDao.
 * TODO
 *
 * @author gpanthe
 * @since 7/05/2014
 */
public interface MongeezDao {
    void runScript(String code);

    DB getDb();

    void updateCollection(String collection, DBObject query, DBObject update, boolean upsert, boolean multi);

    void insertCollection(String collection, DBObject params);

    boolean wasExecuted(ChangeSet changeSet);

    void logChangeSet(ChangeSet changeSet);

    void ensureIndex(String collection, DBObject params, DBObject options);
}
