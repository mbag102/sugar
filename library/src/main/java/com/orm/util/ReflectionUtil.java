package com.orm.util;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.util.Log;

import com.google.common.collect.ListMultimap;
import com.orm.SugarRecord;
import com.orm.dsl.Id;
import com.orm.dsl.Ignore;
import com.orm.dsl.Relationship;
import com.orm.dsl.Table;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReflectionUtil {

    public static Set<SugarRecord> getRecordsToSave(Object object, Set<SugarRecord> recordsToSave, ListMultimap<String, ContentValues> joinTables) {
        Class table = object.getClass();

        Log.d("Sugar", "Fetching properties");
        List<Field> typeFields = new ArrayList<Field>();

        getAllFields(typeFields, table);

        for (Field field : typeFields) {
            if(field.isAnnotationPresent(Relationship.class)) {

                field.setAccessible(true);
                Class<?> columnType = field.getType();
                Object columnValue = null;

                try {
                    columnValue = field.get(object);
                } catch(IllegalAccessException e) {
                    e.printStackTrace();
                }

                Relationship relationship = field.getAnnotation(Relationship.class);

                if (Collection.class.isAssignableFrom(columnType)) {

                    //Explicitly invoke getter instead of grabbing value from field to be safe that we don't omit getter logic
                    //Try get{fieldName}
                    try {
                        Method getter = table.getMethod("get" + field.getName().substring(0, 1).toUpperCase() + field.getName().substring(1));
                        columnValue = getter.invoke(object, (Object[]) null);

                        //Try is{fieldName}
                    } catch (Exception e) {
                        try {
                            Method getter = table.getMethod("is" + field.getName().substring(0, 1).toUpperCase() + field.getName().substring(1));
                            columnValue = getter.invoke(object, (Object[]) null);
                            //No getter available. Get from field
                        } catch (Exception e1) {
                            //DO NOTHING: columnValue already = columnValue
                        }
                    }

                    if (columnValue != null) {
                        for (Object child : (Collection) columnValue) {

                            if(child == null) continue;

                            //They should be
                            if (SugarRecord.isSugarEntity(child.getClass())) {

                                boolean success = true;

                                if(((Relationship) field.getAnnotation(Relationship.class)).cascade()) {
                                    success = recordsToSave.add((SugarRecord) child);
                                }

                                //If not then it means it is a bidirectional relationship and we don't want it showing up twice
                                //Even if there is no cascading, we still want to hook up the relationship.
                                //NOTE: If cascading = false, the child object must already exist or this will fail because no Id will be present.
                                if(success) {

                                    ContentValues contentValues = new ContentValues(2);
                                    contentValues.put(relationship.objectIdName(), ((SugarRecord) object).getId());
                                    contentValues.put(relationship.refObjectIdName(), ((SugarRecord) child).getId());

                                    joinTables.put(relationship.joinTable(), contentValues);

                                    if(((Relationship) field.getAnnotation(Relationship.class)).cascade()) {
                                        ReflectionUtil.getRecordsToSave(child, recordsToSave, joinTables);
                                    }
                                }
                            } else {
                                break;
                            }
                        }
                    }
                } else if(columnValue != null && SugarRecord.isSugarEntity(columnValue.getClass())) {


                    //Explicitly invoke getter instead of grabbing value from field to be safe that we don't omit getter logic
                    //Try get{fieldName}
                    try {
                        Method getter = object.getClass().getMethod("get" + field.getName().substring(0, 1).toUpperCase() + field.getName().substring(1));
                        columnValue = getter.invoke(object, (Object[]) null);

                        //Try is{fieldName}
                    } catch (Exception e) {
                        try {
                            Method getter = object.getClass().getMethod("is" + field.getName().substring(0, 1).toUpperCase() + field.getName().substring(1));
                            columnValue = getter.invoke(object, (Object[]) null);
                            //No getter available. Get from field
                        } catch (Exception e1) {
                            //DO NOTHING: columnValue already = columnValue
                        }
                    }

                    if(columnValue != null) {
                        boolean success = recordsToSave.add((SugarRecord) columnValue);

                        //If not then it means it is a bidirectional relationship and we don't want it showing up twice
                        if(success) {

                            ContentValues contentValues = new ContentValues(2);
                            contentValues.put(relationship.objectIdName(), ((SugarRecord) object).getId());
                            contentValues.put(relationship.refObjectIdName(), ((SugarRecord) columnValue).getId());

                            joinTables.put(relationship.joinTable(), contentValues);
                            ReflectionUtil.getRecordsToSave(columnValue, recordsToSave, joinTables);
                        }
                    }
                }
            }
        }

        return recordsToSave;
    }

    public static List<Field> getTableFields(Class table) {

        List<Field> fieldList = SugarConfig.getFields(table);
        if (fieldList != null) return fieldList;

        Log.d("Sugar", "Fetching properties");
        List<Field> typeFields = new ArrayList<>();

        getAllFields(typeFields, table);

        List<Field> toStore = new ArrayList<>();
        for (Field field : typeFields) {
            if (!field.isAnnotationPresent(Ignore.class) && !Modifier.isStatic(field.getModifiers()) && !Modifier.isTransient(field.getModifiers())) {
                toStore.add(field);
            }
        }

        SugarConfig.setFields(table, toStore);
        return toStore;
    }

    private static List<Field> getAllFields(List<Field> fields, Class<?> type) {
        Collections.addAll(fields, type.getDeclaredFields());

        if (type.getSuperclass() != null) {
            fields = getAllFields(fields, type.getSuperclass());
        }

        return fields;
    }

    public static List<ContentValues> addFieldValueToColumn(ContentValues values, Field column, Object object,
                                             Map<Object, Long> entitiesMap) {

        List<ContentValues> relationshipList = null;

        column.setAccessible(true);
        Class<?> columnType = column.getType();
        try {
            String columnName = NamingHelper.toSQLName(column);
            Object columnValue = column.get(object);

            if (columnType.isAnnotationPresent(Table.class)) {
                Field field;
                try {
                    Table table = columnType.getAnnotation(Table.class);
                    field = columnType.getDeclaredField(table.primaryKeyField());
                    field.setAccessible(true);
                    values.put(columnName,
                            (field != null)
                                    ? String.valueOf(field.get(columnValue)) : "0");
                } catch (NoSuchFieldException e) {
                    if (entitiesMap.containsKey(columnValue)) {
                        values.put(columnName, entitiesMap.get(columnValue));
                    }
                }
            } else if (SugarRecord.class.isAssignableFrom(columnType)) {
                values.put(columnName,
                        (columnValue != null)
                                ? String.valueOf(((SugarRecord) columnValue).getId())
                                : "0");
            } else {
                if (columnType.equals(Short.class) || columnType.equals(short.class)) {
                    values.put(columnName, (Short) columnValue);
                } else if (columnType.equals(Integer.class) || columnType.equals(int.class)) {
                    values.put(columnName, (Integer) columnValue);
                } else if (columnType.equals(Long.class) || columnType.equals(long.class)) {
                    values.put(columnName, (Long) columnValue);
                } else if (columnType.equals(Float.class) || columnType.equals(float.class)) {
                    values.put(columnName, (Float) columnValue);
                } else if (columnType.equals(Double.class) || columnType.equals(double.class)) {
                    values.put(columnName, (Double) columnValue);
                } else if (columnType.equals(Boolean.class) || columnType.equals(boolean.class)) {
                    values.put(columnName, (Boolean) columnValue);
                } else if (columnType.equals(BigDecimal.class)) {
                    try {
                        values.put(columnName, column.get(object).toString());
                    } catch (NullPointerException e) {
                        values.putNull(columnName);
                    }
                } else if (Timestamp.class.equals(columnType)) {
                    try {
                        values.put(columnName, ((Timestamp) column.get(object)).getTime());
                    } catch (NullPointerException e) {
                        values.put(columnName, (Long) null);
                    }
                } else if (Date.class.equals(columnType)) {
                    try {
                        values.put(columnName, ((Date) column.get(object)).getTime());
                    } catch (NullPointerException e) {
                        values.put(columnName, (Long) null);
                    }
                } else if (Calendar.class.equals(columnType)) {
                    try {
                        values.put(columnName, ((Calendar) column.get(object)).getTimeInMillis());
                    } catch (NullPointerException e) {
                        values.put(columnName, (Long) null);
                    }
                } else if (columnType.equals(byte[].class)) {
                    if (columnValue == null) {
                        values.put(columnName, "".getBytes());
                    } else {
                        values.put(columnName, (byte[]) columnValue);
                    }
                } else if(column.isAnnotationPresent(Relationship.class)) {
                    Relationship relationship = column.getAnnotation(Relationship.class);

                    relationshipList = new ArrayList<ContentValues>();

                    if(Collection.class.isAssignableFrom(columnType)) {

                        //Explicitly invoke getter instead of grabbing value from field to be safe that we don't omit getter logic
                        //Try get{fieldName}
                        try {
                            Method getter = object.getClass().getMethod("get" + column.getName().substring(0, 1).toUpperCase() + column.getName().substring(1));
                            columnValue = getter.invoke(object, (Object[]) null);

                            //Try is{fieldName}
                        } catch (Exception e) {
                            try {
                                Method getter = object.getClass().getMethod("is" + column.getName().substring(0, 1).toUpperCase() + column.getName().substring(1));
                                columnValue = getter.invoke(object, (Object[]) null);
                                //No getter available. Get from field
                            } catch (Exception e1) {
                                //DO NOTHING: columnValue already = columnValue
                            }
                        }

                        if(columnValue != null) {
                            for (Object child : (Collection) columnValue) {
                                //They should be
                                if (SugarRecord.isSugarEntity(child.getClass())) {
                                    ContentValues contentValues = new ContentValues(2);
                                    contentValues.put(relationship.objectIdName(), ((SugarRecord) object).getId());
                                    contentValues.put(relationship.refObjectIdName(), ((SugarRecord) child).getId());

                                    relationshipList.add(contentValues);
                                } else {
                                    break;
                                }
                            }
                        }
                    } else if(columnValue != null && SugarRecord.isSugarEntity(columnValue.getClass())) {


                        //Explicitly invoke getter instead of grabbing value from field to be safe that we don't omit getter logic
                        //Try get{fieldName}
                        try {
                            Method getter = object.getClass().getMethod("get" + column.getName().substring(0, 1).toUpperCase() + column.getName().substring(1));
                            columnValue = getter.invoke(object, (Object[]) null);

                            //Try is{fieldName}
                        } catch (Exception e) {
                            try {
                                Method getter = object.getClass().getMethod("is" + column.getName().substring(0, 1).toUpperCase() + column.getName().substring(1));
                                columnValue = getter.invoke(object, (Object[]) null);
                                //No getter available. Get from field
                            } catch (Exception e1) {
                                //DO NOTHING: columnValue already = columnValue
                            }
                        }

                        if(columnValue != null) {
                            ContentValues contentValues = new ContentValues(2);
                            contentValues.put(relationship.objectIdName(), ((SugarRecord) columnValue).getId());
                            contentValues.put(relationship.refObjectIdName(), ((SugarRecord) object).getId());

                            relationshipList.add(contentValues);
                        }
                    }

                } else {
                    if (columnValue == null) {
                        values.putNull(columnName);
                    } else if (columnType.isEnum()) {
                        values.put(columnName, ((Enum) columnValue).name());
                    } else {
                        values.put(columnName, String.valueOf(columnValue));
                    }
                }
            }

        } catch (IllegalAccessException e) {
            Log.e("Sugar", e.getMessage());
        }

        return relationshipList;
    }

    public static void setFieldValueFromCursor(Cursor cursor, Field field, Object object) {
        field.setAccessible(true);
        try {
            Class fieldType = field.getType();
            String colName = NamingHelper.toSQLName(field);

            int columnIndex = cursor.getColumnIndex(colName);

            //TODO auto upgrade to add new columns
            if (columnIndex < 0) {
                Log.e("SUGAR", "Invalid colName, you should upgrade database");
                return;
            }

            if (cursor.isNull(columnIndex)) {
                return;
            }

            if (colName.equalsIgnoreCase("id") || field.isAnnotationPresent(Id.class)) {
                long cid = cursor.getLong(columnIndex);
                field.set(object, cid);
            } else if (fieldType.equals(long.class) || fieldType.equals(Long.class)) {
                field.set(object,
                        cursor.getLong(columnIndex));
            } else if (fieldType.equals(String.class)) {
                String val = cursor.getString(columnIndex);
                field.set(object, val != null && val.equals("null") ? null : val);
            } else if (fieldType.equals(double.class) || fieldType.equals(Double.class)) {
                field.set(object,
                        cursor.getDouble(columnIndex));
            } else if (fieldType.equals(boolean.class) || fieldType.equals(Boolean.class)) {
                field.set(object,
                        cursor.getString(columnIndex).equals("1"));
            } else if (fieldType.equals(int.class) || fieldType.equals(Integer.class)) {
                field.set(object,
                        cursor.getInt(columnIndex));
            } else if (fieldType.equals(float.class) || fieldType.equals(Float.class)) {
                field.set(object,
                        cursor.getFloat(columnIndex));
            } else if (fieldType.equals(short.class) || fieldType.equals(Short.class)) {
                field.set(object,
                        cursor.getShort(columnIndex));
            } else if (fieldType.equals(BigDecimal.class)) {
                String val = cursor.getString(columnIndex);
                field.set(object, val != null && val.equals("null") ? null : new BigDecimal(val));
            } else if (fieldType.equals(Timestamp.class)) {
                long l = cursor.getLong(columnIndex);
                field.set(object, new Timestamp(l));
            } else if (fieldType.equals(Date.class)) {
                long l = cursor.getLong(columnIndex);
                field.set(object, new Date(l));
            } else if (fieldType.equals(Calendar.class)) {
                long l = cursor.getLong(columnIndex);
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(l);
                field.set(object, c);
            } else if (fieldType.equals(byte[].class)) {
                byte[] bytes = cursor.getBlob(columnIndex);
                if (bytes == null) {
                    field.set(object, "".getBytes());
                } else {
                    field.set(object, cursor.getBlob(columnIndex));
                }
            } else if (Enum.class.isAssignableFrom(fieldType)) {
                try {
                    Method valueOf = field.getType().getMethod("valueOf", String.class);
                    String strVal = cursor.getString(columnIndex);
                    Object enumVal = valueOf.invoke(field.getType(), strVal);
                    field.set(object, enumVal);
                } catch (Exception e) {
                    Log.e("Sugar", "Enum cannot be read from Sqlite3 database. Please check the type of field " + field.getName());
                }
            } else
                Log.e("Sugar", "Class cannot be read from Sqlite3 database. Please check the type of field " + field.getName() + "(" + field.getType().getName() + ")");
        } catch (IllegalArgumentException | IllegalAccessException e) {
            Log.e("field set error", e.getMessage());
        }
    }

    private static Field getDeepField(String fieldName, Class<?> type) throws NoSuchFieldException {
        try {
            return type.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            Class superclass = type.getSuperclass();
            if (superclass != null) {
                return getDeepField(fieldName, superclass);
            } else {
                throw e;
            }
        }
    }

    /*
    public static void setFieldValueForId(Object object, Long value) {
        try {
            Field field = getDeepField("id", object.getClass());
            field.setAccessible(true);
            field.set(object, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }*/

    public static List<Class> getDomainClasses() {
        List<Class> domainClasses = new ArrayList<>();
        try {
            for (String className : getAllClasses()) {
                Class domainClass = getDomainClass(className);
                if (domainClass != null) domainClasses.add(domainClass);
            }
        } catch (IOException | PackageManager.NameNotFoundException  e) {
            Log.e("Sugar", e.getMessage());
        }

        return domainClasses;
    }


    private static Class getDomainClass(String className) {
        Class<?> discoveredClass = null;
        try {
            discoveredClass = Class.forName(className, true, Thread.currentThread().getContextClassLoader());
        } catch (Throwable e) {
            String error = (e.getMessage() == null) ? "getDomainClass " + className + " error" : e.getMessage();
            Log.e("Sugar", error);
        }

        if ((discoveredClass != null) &&
                ((SugarRecord.class.isAssignableFrom(discoveredClass) &&
                        !SugarRecord.class.equals(discoveredClass)) ||
                        discoveredClass.isAnnotationPresent(Table.class)) &&
                !Modifier.isAbstract(discoveredClass.getModifiers())) {

            Log.i("Sugar", "domain class : " + discoveredClass.getSimpleName());
            return discoveredClass;

        } else {
            return null;
        }
    }


    private static List<String> getAllClasses() throws PackageManager.NameNotFoundException, IOException {
        String packageName = ManifestHelper.getDomainPackageName();
        List<String> classNames = new ArrayList<>();
        try {
            List<String> allClasses = MultiDexHelper.getAllClasses();
            for (String classString : allClasses) {
                if (classString.startsWith(packageName)) classNames.add(classString);
            }
        } catch (NullPointerException e) {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> urls = classLoader.getResources("");
            while (urls.hasMoreElements()) {
                List<String> fileNames = new ArrayList<>();
                String classDirectoryName = urls.nextElement().getFile();
                if (classDirectoryName.contains("bin") || classDirectoryName.contains("classes")
                        || classDirectoryName.contains("retrolambda")) {
                    File classDirectory = new File(classDirectoryName);
                    for (File filePath : classDirectory.listFiles()) {
                        populateFiles(filePath, fileNames, "");
                    }
                    for (String fileName : fileNames) {
                        if (fileName.startsWith(packageName)) classNames.add(fileName);
                    }
                }
            }
        }
//        } finally {
//            if (null != dexfile) dexfile.close();
//        }

        return classNames;
    }

    private static void populateFiles(File path, List<String> fileNames, String parent) {
        if (path.isDirectory()) {
            for (File newPath : path.listFiles()) {
                if ("".equals(parent)) {
                    populateFiles(newPath, fileNames, path.getName());
                } else {
                    populateFiles(newPath, fileNames, parent + "." + path.getName());
                }
            }
        } else {
            String pathName = path.getName();
            String classSuffix = ".class";
            pathName = pathName.endsWith(classSuffix) ?
                    pathName.substring(0, pathName.length() - classSuffix.length()) : pathName;
            if ("".equals(parent)) {
                fileNames.add(pathName);
            } else {
                fileNames.add(parent + "." + pathName);
            }
        }
    }

    private static String getSourcePath(Context context) throws PackageManager.NameNotFoundException {
        return context.getPackageManager().getApplicationInfo(context.getPackageName(), 0).sourceDir;
    }
}
