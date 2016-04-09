package com.orm;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import android.text.TextUtils;
import android.util.Log;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.orm.dsl.Id;
import com.orm.dsl.Relationship;
import com.orm.dsl.Table;
import com.orm.dsl.Unique;
import com.orm.util.ManifestHelper;
import com.orm.util.NamingHelper;
import com.orm.util.QueryBuilder;
import com.orm.util.ReflectionUtil;
import com.orm.util.SugarCursor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import static com.orm.SugarContext.getSugarContext;

public class SugarRecord {
    public static final String SUGAR = "Sugar";

    private Long id = null;

    private static SQLiteDatabase getSugarDataBase() {
        return getSugarContext().getSugarDb().getDB();
    }

    public static <T> int deleteAll(Class<T> type) {
        return deleteAll(type, null);
    }

    public static <T> int deleteAll(Class<T> type, String whereClause, String... whereArgs) {
        return getSugarDataBase().delete(NamingHelper.toSQLName(type), whereClause, whereArgs);
    }

    public static <T> Cursor getCursor(Class<T> type, String whereClause, String[] whereArgs, String groupBy, String orderBy, String limit) {
        Cursor raw = getSugarDataBase().query(NamingHelper.toSQLName(type), null, whereClause, whereArgs,
                groupBy, null, orderBy, limit);
        return new SugarCursor(raw);
    }

    @SuppressWarnings("deprecation")
    public static <T> void saveInTx(T... objects) {
        saveInTx(Arrays.asList(objects));
    }

    @SuppressWarnings("deprecation")
    public static <T> void saveInTx(Collection<T> objects) {
        SQLiteDatabase sqLiteDatabase = getSugarDataBase();
        try {
            sqLiteDatabase.beginTransaction();
            sqLiteDatabase.setLockingEnabled(false);
            for (T object: objects) {
                save(getSugarContext().getSugarDb().getDB(), object);
            }
            sqLiteDatabase.setTransactionSuccessful();
        } catch (Exception e) {
            Log.i(SUGAR, "Error in saving in transaction " + e.getMessage());
        } finally {
            sqLiteDatabase.endTransaction();
            sqLiteDatabase.setLockingEnabled(true);
        }
    }

    @SuppressWarnings("deprecation")
    public static <T> void updateInTx(T... objects) {
        updateInTx(Arrays.asList(objects));
    }

    @SuppressWarnings("deprecation")
    public static <T> void updateInTx(Collection<T> objects) {
        SQLiteDatabase sqLiteDatabase = getSugarDataBase();
        try {
            sqLiteDatabase.beginTransaction();
            sqLiteDatabase.setLockingEnabled(false);
            for (T object: objects) {
                update(object);
            }
            sqLiteDatabase.setTransactionSuccessful();
        } catch (Exception e) {
            Log.i(SUGAR, "Error in saving in transaction " + e.getMessage());
        } finally {
            sqLiteDatabase.endTransaction();
            sqLiteDatabase.setLockingEnabled(true);
        }
    }

    @SuppressWarnings("deprecation")
    public static <T> int deleteInTx(T... objects) {
        return deleteInTx(Arrays.asList(objects));
    }

    @SuppressWarnings("deprecation")
    public static <T> int deleteInTx(Collection<T> objects) {
        SQLiteDatabase sqLiteDatabase = getSugarDataBase();
        int deletedRows = 0;
        try {
            sqLiteDatabase.beginTransaction();
            sqLiteDatabase.setLockingEnabled(false);
            for (T object : objects) {
                if (delete(object)) {
                    ++deletedRows;
                }
            }
            sqLiteDatabase.setTransactionSuccessful();
        } catch (Exception e) {
            deletedRows = 0;
            Log.i(SUGAR, "Error in deleting in transaction " + e.getMessage());
        } finally {
            sqLiteDatabase.endTransaction();
            sqLiteDatabase.setLockingEnabled(true);
        }
        return deletedRows;
    }

    public static <T> List<T> listAll(Class<T> type) {
        return find(type, null, null, null, null, null);
    }
    
    public static <T> List<T> listAll(Class<T> type, String orderBy) {
        return find(type, null, null, null, orderBy, null);
    }

    public static <T> T findById(Class<T> type, Long id) {
        List<T> list = find(type, "id=?", new String[]{String.valueOf(id)}, null, null, "1");
        if (list.isEmpty()) return null;
        return list.get(0);
    }

    public static <T> T findById(Class<T> type, Integer id) {
        return findById(type, Long.valueOf(id));
    }

    public static <T> List<T> findById(Class<T> type, String[] ids) {
        String whereClause = "id IN (" + QueryBuilder.generatePlaceholders(ids.length) + ")";
        return find(type, whereClause, ids);
    }

    public static <T> T first(Class<T>type) {
        List<T> list = findWithQuery(type,
                "SELECT * FROM " + NamingHelper.toSQLName(type) + " ORDER BY ID ASC LIMIT 1");
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    public static <T> T last(Class<T>type) {
        List<T> list = findWithQuery(type,
                "SELECT * FROM " + NamingHelper.toSQLName(type) + " ORDER BY ID DESC LIMIT 1");
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    public static <T> Iterator<T> findAll(Class<T> type) {
        return findAsIterator(type, null, null, null, null, null);
    }

    public static <T> Iterator<T> findAsIterator(Class<T> type, String whereClause, String... whereArgs) {
        return findAsIterator(type, whereClause, whereArgs, null, null, null);
    }

    public static <T> Iterator<T> findWithQueryAsIterator(Class<T> type, String query, String... arguments) {
        Cursor cursor = getSugarDataBase().rawQuery(query, arguments);
        return new CursorIterator<>(type, cursor);
    }

    public static <T> Iterator<T> findAsIterator(Class<T> type, String whereClause, String[] whereArgs, String groupBy, String orderBy, String limit) {
        Cursor cursor = getSugarDataBase().query(NamingHelper.toSQLName(type), null, whereClause, whereArgs,
                groupBy, null, orderBy, limit);
        return new CursorIterator<>(type, cursor);
    }

    public static <T> List<T> find(Class<T> type, String whereClause, String... whereArgs) {
        return find(type, whereClause, whereArgs, null, null, null);
    }

    public static <T> List<T> findWithQuery(Class<T> type, String query, String... arguments) {
        Cursor cursor = getSugarDataBase().rawQuery(query, arguments);

        return  getEntitiesFromCursor(cursor, type);
    }

    public static void executeQuery(String query, String... arguments) {
        getSugarDataBase().execSQL(query, arguments);
    }

    public static <T> List<T> findByRelationship(Class<T> type, Relationship relationship, String where, String groupBy, String orderBy, String limit) {
        return findByRelationship(type, relationship.joinTable(), relationship.refObjectIdName(), relationship.joinColumnName(), where, groupBy, orderBy, limit);
    }

    public static <T> List<T> findByRelationship(Class<T> type, String joinTable, String objectIdName, String where, String groupBy, String orderBy, String limit) {
        return findByRelationship(type, joinTable, objectIdName, "ID", where, groupBy, orderBy, limit);
    }

    public static <T> List<T> findByRelationship(Class<T> type, String joinTable, String objectIdName, String joinColumnName, String where, String groupBy, String orderBy, String limit) {
        T entity;
        List<T> toRet = new ArrayList<T>();

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM ").append(NamingHelper.toSQLName(type))
                .append(" a INNER JOIN ").append(joinTable).append(" b ON a.")
                .append(joinColumnName)
                .append("=b.").append(objectIdName);
        appendClause(sb, " WHERE ", where);
        appendClause(sb, " GROUP BY ", groupBy);
        //appendClause(sb, " HAVING ", having);
        appendClause(sb, " ORDER BY ", orderBy);
        appendClause(sb, " LIMIT ", limit);

        Log.v("SUGAR", "Join table sql: " + sb.toString());

        Cursor c = getSugarDataBase().rawQuery(sb.toString(), new String[0]);

        try {
            while (c.moveToNext()) {
                entity = type.getDeclaredConstructor().newInstance();
                inflate(c, entity, getSugarContext().getEntitiesMap());
                toRet.add(entity);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            c.close();
        }
        return toRet;
    }

    public static List<Long> executeRawQuery(String query) {
        List<Long> ids = new ArrayList<Long>();

        Cursor cursor = getSugarDataBase().rawQuery(query, new String[0]);

        try {
            while (cursor.moveToNext()) {
                ids.add(cursor.getLong(cursor.getColumnIndex("ID")));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            cursor.close();
        }
        return ids;
    }

    private static void appendClause(StringBuilder s, String name, String clause) {
        if (!TextUtils.isEmpty(clause)) {
            s.append(name);
            s.append(clause);
        }
    }

    public static <T> T findUnique(Class<T> type, String whereClause, String[] whereArgs) {
        List<T> list = find(type, whereClause, whereArgs, null, null, null);
        if(list == null || list.isEmpty()) {
            return null;
        }

        if(list.size() > 1) {
            throw new IllegalStateException("Find unique was called but database contained more than one matching result.");
        }

        return list.get(0);
    }

    public static <T> List<T> find(Class<T> type, String whereClause, String[] whereArgs, String groupBy, String orderBy, String limit) {

        String args[];
        args = (whereArgs == null) ? null : replaceArgs(whereArgs);

        Cursor cursor = getSugarDataBase().query(NamingHelper.toSQLName(type), null, whereClause, args,
                groupBy, null, orderBy, limit);

        return getEntitiesFromCursor(cursor, type);
    }

    public static <T> List<T> getEntitiesFromCursor(Cursor cursor, Class<T> type){
        T entity;
        List<T> result = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                entity = type.getDeclaredConstructor().newInstance();
                inflate(cursor, entity, getSugarContext().getEntitiesMap());
                result.add(entity);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            cursor.close();
        }

        return result;
    }

    public static <T> long count(Class<T> type) {
        return count(type, null, null, null, null, null);
    }

    public static <T> long count(Class<T> type, String whereClause, String[] whereArgs) {
    	return count(type, whereClause, whereArgs, null, null, null);
    }

    public static <T> long count(Class<T> type, String whereClause, String[] whereArgs, String groupBy, String orderBy, String limit) {
        long result = -1;
        String filter = (!TextUtils.isEmpty(whereClause)) ? " where "  + whereClause : "";
        SQLiteStatement sqliteStatement;
        try {
            sqliteStatement = getSugarDataBase().compileStatement("SELECT count(*) FROM " + NamingHelper.toSQLName(type) + filter);
        } catch (SQLiteException e) {
            e.printStackTrace();
            return result;
        }

        if (whereArgs != null) {
            for (int i = whereArgs.length; i != 0; i--) {
                sqliteStatement.bindString(i, whereArgs[i - 1]);
            }
        }

        try {
            result = sqliteStatement.simpleQueryForLong();
        } finally {
            sqliteStatement.close();
        }

        return result;
    }

    public static long save(Object object) {
        return save(getSugarDataBase(), object);
    }

    static void saveJoinTableList(SQLiteDatabase db, List<ContentValues> relationshipList, String joinTableName) {

        for(ContentValues values: relationshipList) {

            //If record already exists then ignore
            long id = db.insertWithOnConflict(joinTableName, null, values,
                    SQLiteDatabase.CONFLICT_IGNORE);

            Log.i("Sugar", "Inserted Join table record for " + joinTableName + ".");

        }
    }

    static void saveJoinTable(SQLiteDatabase db, ContentValues values, String joinTableName) {

        //If record already exists then ignore
        long id = db.insertWithOnConflict(joinTableName, null, values,
                SQLiteDatabase.CONFLICT_IGNORE);

        Log.i("Sugar", "Inserted Join table record for " + joinTableName + ".");

    }


    static long save(SQLiteDatabase db, Object object) {

        Set<SugarRecord> recordsToSave = new HashSet<SugarRecord>();
        ListMultimap<String, ContentValues> joinTables = ArrayListMultimap.create();
        ReflectionUtil.getRecordsToSave(object, recordsToSave, joinTables);

        if(recordsToSave != null && !recordsToSave.isEmpty()) {
            saveInTx(recordsToSave);

            if(joinTables != null) {
                for(String tableName: joinTables.keySet()) {
                    saveJoinTableList(getSugarContext().getSugarDb().getDB(), joinTables.get(tableName), tableName);
                }
            }
        }

        Map<Object, Long> entitiesMap = getSugarContext().getEntitiesMap();

        List<Field> columns = ReflectionUtil.getTableFields(object.getClass());
        ContentValues values = new ContentValues(columns.size());
        Field idField = null;
        boolean isIdAnnotationPresent = false;
        for (Field column : columns) {
            List<SugarRecord> children = new ArrayList<SugarRecord>();
            if(column.isAnnotationPresent(Id.class)) {

                if(isIdAnnotationPresent) {
                    throw new IllegalStateException("Multiple Id annotations present on " + object.getClass().getSimpleName() + ". Only one field can have this annotation.");
                }

                idField = column;
                isIdAnnotationPresent = true;
            }

            //Check for null to make sure we don't squash a declared annotated ID field
            else if (column.getName().equals("id") && idField == null) {
                idField = column;
            }

            if(!column.getName().equals("id")) {
                ReflectionUtil.addFieldValueToColumn(values, column, object, entitiesMap);
            }

        }

        ReflectionUtil.addFieldValueToColumn(values, idField, object, entitiesMap);

        boolean isSugarEntity = isSugarEntity(object.getClass());
        if (isSugarEntity && entitiesMap.containsKey(object) && !isIdAnnotationPresent) {
                values.put("id", entitiesMap.get(object));
        } else if(isSugarEntity && isIdAnnotationPresent && idField != null) {
            idField.setAccessible(true);
            try {
                Object id = idField.get(object);
                if(id != null && (Long.class.equals(id.getClass()) || long.class.equals(id.getClass()))) {
                    values.put("id", (Long) id);
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }

        }

        long id = db.insertWithOnConflict(NamingHelper.toSQLName(object.getClass()), null, values,
                SQLiteDatabase.CONFLICT_REPLACE);

        if (object.getClass().isAnnotationPresent(Table.class)) {
            if (idField != null) {
                idField.setAccessible(true);
                try {
                    idField.set(object, id);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            } else {
                entitiesMap.put(object, id);
            }
        } else if (SugarRecord.class.isAssignableFrom(object.getClass())) {
            ((SugarRecord) object).setId(id);
        }

        if (ManifestHelper.isDebugEnabled()) {
            Log.i(SUGAR, object.getClass().getSimpleName() + " saved : " + id);
        }

        return id;
    }

    public static long update(Object object) {
        return update(getSugarDataBase(), object);
    }

    static long update(SQLiteDatabase db, Object object) {
        Map<Object, Long> entitiesMap = getSugarContext().getEntitiesMap();
        List<Field> columns = ReflectionUtil.getTableFields(object.getClass());
        ContentValues values = new ContentValues(columns.size());

        StringBuilder whereClause = new StringBuilder();
        List<String> whereArgs = new ArrayList<>();

        for (Field column : columns) {
            if(column.isAnnotationPresent(Unique.class)) {
                try {
                    column.setAccessible(true);
                    String columnName = NamingHelper.toSQLName(column);
                    Object columnValue = column.get(object);

                    whereClause.append(columnName).append(" = ?");
                    whereArgs.add(String.valueOf(columnValue));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            } else {
                if (!column.getName().equals("id")) {
                    ReflectionUtil.addFieldValueToColumn(values, column, object, entitiesMap);
                }
            }
        }

        String[] whereArgsArray = whereArgs.toArray(new String[whereArgs.size()]);
        // Get SugarRecord based on Unique values
        long rowsEffected = db.update(NamingHelper.toSQLName(object.getClass()), values, whereClause.toString(), whereArgsArray);

        if (rowsEffected == 0) {
            return save(db, object);
        } else {
            return rowsEffected;
        }
    }



    public static boolean isSugarEntity(Class<?> objectClass) {
        return objectClass.isAnnotationPresent(Table.class) || SugarRecord.class.isAssignableFrom(objectClass);
    }

    private static void inflate(Cursor cursor, Object object, Map<Object, Long> entitiesMap) {
        List<Field> columns = ReflectionUtil.getTableFields(object.getClass());
        if (!entitiesMap.containsKey(object)) {
            entitiesMap.put(object, cursor.getLong(cursor.getColumnIndex(("ID"))));
        }

        for (Field field : columns) {
        	field.setAccessible(true);
            Class<?> fieldType = field.getType();
            if (isSugarEntity(fieldType)) {
                try {
                    long id = cursor.getLong(cursor.getColumnIndex(NamingHelper.toSQLName(field)));
                    field.set(object, (id > 0) ? findById(fieldType, id) : null);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            } else {
                ReflectionUtil.setFieldValueFromCursor(cursor, field, object);
            }
        }
    }

    public boolean delete() {
        Long id = getId();
        Class<?> type = getClass();
        if (id != null && id > 0L) {
            Log.i(SUGAR, type.getSimpleName() + " deleted : " + id);
            return getSugarDataBase().delete(NamingHelper.toSQLName(type), "Id=?", new String[]{id.toString()}) == 1;
        } else {
            Log.i(SUGAR, "Cannot delete object: " + type.getSimpleName() + " - object has not been saved");
            return false;
        }
    }
    
    public static boolean delete(Object object) {
        Class<?> type = object.getClass();
        if (type.isAnnotationPresent(Table.class)) {
            try {

                Table table = type.getAnnotation(Table.class);

                Field field = type.getDeclaredField(table.primaryKeyField());
                field.setAccessible(true);
                Long id = (Long) field.get(object);
                if (id != null && id > 0L) {
                    boolean deleted = getSugarDataBase().delete(NamingHelper.toSQLName(type), "Id=?", new String[]{id.toString()}) == 1;
                    Log.i(SUGAR, type.getSimpleName() + " deleted : " + id);
                    return deleted;
                } else {
                    Log.i(SUGAR, "Cannot delete object: " + object.getClass().getSimpleName() + " - object has not been saved");
                    return false;
                }
            } catch (NoSuchFieldException e) {
                Log.i(SUGAR, "Cannot delete object: " + object.getClass().getSimpleName() + " - annotated object has no id");
                return false;
            } catch (IllegalAccessException e) {
                Log.i(SUGAR, "Cannot delete object: " + object.getClass().getSimpleName() + " - can't access id");
                return false;
            }
        } else if (SugarRecord.class.isAssignableFrom(type)) {
            return ((SugarRecord) object).delete();
        } else {
            Log.i(SUGAR, "Cannot delete object: " + object.getClass().getSimpleName() + " - not persisted");
            return false;
        }
    }

    public long save() {
        return save(getSugarDataBase(), this);
    }

    public long update() {
        return update(getSugarDataBase(), this);
    }

    @SuppressWarnings("unchecked")
    void inflate(Cursor cursor) {
        inflate(cursor, this, getSugarContext().getEntitiesMap());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public static class CursorIterator<E> implements Iterator<E> {
        Class<E> type;
        Cursor cursor;

        public CursorIterator(Class<E> type, Cursor cursor) {
            this.type = type;
            this.cursor = cursor;
        }

        @Override
        public boolean hasNext() {
            return cursor != null && !cursor.isClosed() && !cursor.isAfterLast();
        }

        @Override
        public E next() {
            E entity = null;
            if (cursor == null || cursor.isAfterLast()) {
                throw new NoSuchElementException();
            }

            if (cursor.isBeforeFirst()) {
                cursor.moveToFirst();
            }

            try {
                entity = type.getDeclaredConstructor().newInstance();
                inflate(cursor, entity, getSugarContext().getEntitiesMap());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                cursor.moveToNext();
                if (cursor.isAfterLast()) {
                    cursor.close();
                }
            }

            return entity;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        public E getItemAtPosition(int position) {
            if(cursor.moveToPosition(position)) {
                return this.next();
            } else {
                return null;
            }
        }
        
        public Cursor getCursor() {
            return cursor;
        }
    }

	@Override
    /**
     * Objects are equal if their IDs are equal and their class type is equal
     */
    public boolean equals(Object object) {
        if(object == null) {
            return false;
        }

        if(object.getClass().equals(this.getClass()) && ((SugarRecord) object).getId().equals(this.getId())) {
            return true;
        } else {
            return false;
        }
    }

    public static String[] replaceArgs(String[] args){

        String [] replace = new String[args.length];
        for (int i=0; i<args.length; i++){

            replace[i]= (args[i].equals("true")) ? replace[i]="1" : (args[i].equals("false")) ? replace[i]="0" : args[i];

        }

        return replace;

    }

}
