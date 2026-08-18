/*
 * Copyright (c) 2018, hiwepy (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package schemacrawler.integration.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement; 

public class HiveJdbcClient {

  /*Hive server 版本使用此驱动*/
  //private static String driverName = "org.apache.hadoop.hive.jdbc.HiveDriver";
  /*Hive server 版本使用此驱动*/
  private static String driverName = "org.apache.hive.jdbc.HiveDriver";

  public static void main(String[] args) throws SQLException {

    try {
      Class.forName(driverName);
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      System.exit(1);
    }

    /*hiverserver 版本jdbc url格式*/
    //Connection con = DriverManager.getConnection("jdbc:hive://hostip:10000/default", "", "");

    /*hiverserver2 版本jdbc url格式*/
    Connection con = DriverManager.getConnection("jdbc:hive2://hostip:10000/default", "hive", "hive");
    Statement stmt = con.createStatement();
    //参数设置测试
    //boolean resHivePropertyTest = stmt
    //        .execute("SET tez.runtime.io.sort.mb = 128");
    
    boolean resHivePropertyTest = stmt
            .execute("set hive.execution.engine=tez");
    System.out.println(resHivePropertyTest);

    String tableName = "testHiveDriverTable";
    stmt.executeQuery("drop table " + tableName);
    ResultSet res = stmt.executeQuery("create table " + tableName + " (key int, value string)");

    //show tables
    String sql = "show tables '" + tableName + "'";
    System.out.println("Running: " + sql);
    res = stmt.executeQuery(sql);
    if (res.next()) {
      System.out.println(res.getString(1));
    }

    //describe table
    sql = "describe " + tableName;
    System.out.println("Running: " + sql);
    res = stmt.executeQuery(sql);
    while (res.next()) {
      System.out.println(res.getString(1) + "\t" + res.getString(2));
    } 

    // load data into table
    // NOTE: filepath has to be local to the hive server
    // NOTE: /tmp/a.txt is a ctrl-A separated file with two fields per line
    String filepath = "/tmp/a.txt";
    sql = "load data local inpath '" + filepath + "' into table " + tableName;
    System.out.println("Running: " + sql);
    res = stmt.executeQuery(sql); 

    // select * query
    sql = "select * from " + tableName;
    System.out.println("Running: " + sql);
    res = stmt.executeQuery(sql);
    while (res.next()) {
      System.out.println(String.valueOf(res.getInt(1)) + "\t" + res.getString(2));
    }
    
    // regular hive query
    sql = "select count(1) from " + tableName;
    System.out.println("Running: " + sql);
    res = stmt.executeQuery(sql);
    while (res.next()) {
      System.out.println(res.getString(1));
    }

  }

}