package com.tiscon.dao;

import com.tiscon.domain.*;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.core.NestedRuntimeException;
import org.springframework.dao.DataAccessException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.lang.*;

/**
 * 引越し見積もり機能においてDBとのやり取りを行うクラス。
 *
 * @author Oikawa Yumi
 */
@Component
public class EstimateDao {

    /** データベース・アクセスAPIである「JDBC」を使い、名前付きパラメータを用いてSQLを実行するクラス */
    private final NamedParameterJdbcTemplate parameterJdbcTemplate;

    /**
     * コンストラクタ。
     *
     * @param parameterJdbcTemplate NamedParameterJdbcTemplateクラス
     */
    public EstimateDao(NamedParameterJdbcTemplate parameterJdbcTemplate) {
        this.parameterJdbcTemplate = parameterJdbcTemplate;
    }

    /**
     * 顧客テーブルに登録する。
     *
     * @param customer 顧客情報
     * @return 登録件数
     */
    public int insertCustomer(Customer customer) {
        String sql = "SELECT COUNT(CUSTOMER_NAME) FROM CUSTOMER WHERE OLD_PREFECTURE_ID=:oldPrefectureId AND NEW_PREFECTURE_ID=:newPrefectureId AND CUSTOMER_NAME=:customerName AND TEL=:tel AND EMAIL=:email AND OLD_ADDRESS=:oldAddress AND NEW_ADDRESS=:newAddress AND SCHEDULED_DATE=:scheduledDate";

        if(parameterJdbcTemplate.queryForObject(sql, new BeanPropertySqlParameterSource(customer), Integer.class) > 0) return -1;
        sql = "INSERT INTO CUSTOMER(OLD_PREFECTURE_ID, NEW_PREFECTURE_ID, CUSTOMER_NAME, TEL, EMAIL, OLD_ADDRESS, NEW_ADDRESS, SCHEDULED_DATE)"
                + " VALUES(:oldPrefectureId, :newPrefectureId, :customerName, :tel, :email, :oldAddress, :newAddress, :scheduledDate)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int resultNum = parameterJdbcTemplate.update(sql, new BeanPropertySqlParameterSource(customer), keyHolder);
        customer.setCustomerId(keyHolder.getKey().intValue());
        return resultNum;
    }

    /**
     * オプションサービス_顧客テーブルに登録する。
     *
     * @param optionService オプションサービス_顧客に登録する内容
     * @return 登録件数
     */
    public int insertCustomersOptionService(CustomerOptionService optionService) {
        String sql = "INSERT INTO CUSTOMER_OPTION_SERVICE(CUSTOMER_ID, SERVICE_ID)"
                + " VALUES(:customerId, :serviceId)";
        return parameterJdbcTemplate.update(sql, new BeanPropertySqlParameterSource(optionService));
    }

    /**
     * 顧客_荷物テーブルに登録する。
     *
     * @param packages 登録する荷物
     * @return 登録件数
     */
    public int[] batchInsertCustomerPackage(List<CustomerPackage> packages) {
        String sql = "INSERT INTO CUSTOMER_PACKAGE(CUSTOMER_ID, PACKAGE_ID, PACKAGE_NUMBER)"
                + " VALUES(:customerId, :packageId, :packageNumber)";
        SqlParameterSource[] batch = SqlParameterSourceUtils.createBatch(packages.toArray());

        return parameterJdbcTemplate.batchUpdate(sql, batch);
    }

    /**
     * 都道府県テーブルに登録されているすべての都道府県を取得する。
     *
     * @return すべての都道府県
     */
    public List<Prefecture> getAllPrefectures() {
        String sql = "SELECT PREFECTURE_ID, PREFECTURE_NAME FROM PREFECTURE";
        return parameterJdbcTemplate.query(sql,
                BeanPropertyRowMapper.newInstance(Prefecture.class));
    }

    /**
     * 都道府県間の距離を取得する。
     *
     * @param prefectureIdFrom 引っ越し元の都道府県
     * @param prefectureIdTo   引越し先の都道府県
     * @return 距離[km]
     */
    public double getDistance(String prefectureIdFrom, String prefectureIdTo) {
        // 都道府県のFromとToが逆転しても同じ距離となるため、「そのままの状態のデータ」と「FromとToを逆転させたデータ」をくっつけた状態で距離を取得する。
        String sql = "SELECT DISTANCE FROM (" +
                "SELECT PREFECTURE_ID_FROM, PREFECTURE_ID_TO, DISTANCE FROM PREFECTURE_DISTANCE UNION ALL " +
                "SELECT PREFECTURE_ID_TO PREFECTURE_ID_FROM ,PREFECTURE_ID_FROM PREFECTURE_ID_TO ,DISTANCE FROM PREFECTURE_DISTANCE) " +
                "WHERE PREFECTURE_ID_FROM  = :prefectureIdFrom AND PREFECTURE_ID_TO  = :prefectureIdTo";

        PrefectureDistance prefectureDistance = new PrefectureDistance();
        prefectureDistance.setPrefectureIdFrom(prefectureIdFrom);
        prefectureDistance.setPrefectureIdTo(prefectureIdTo);

        double distance;
        try {
            distance = parameterJdbcTemplate.queryForObject(sql, new BeanPropertySqlParameterSource(prefectureDistance), double.class);
        } catch (IncorrectResultSizeDataAccessException e) {
            distance = 0;
        }
        return distance;
    }

    /**
     * 荷物ごとの段ボール数を取得する。
     *
     * @param packageId 荷物ID
     * @return 段ボール数
     */
    public int getBoxPerPackage(int packageId) {
        String sql = "SELECT BOX FROM PACKAGE_BOX WHERE PACKAGE_ID = :packageId";

        SqlParameterSource paramSource = new MapSqlParameterSource("packageId", packageId);
        return parameterJdbcTemplate.queryForObject(sql, paramSource, Integer.class);
    }

    /**
     * 段ボール数に応じたトラック料金を取得する。
     *
     * @param boxNum 総段ボール数
     * @return 料金[円]
     */
    public int getPricePerTruck(int boxNum) {
        /*
        * トラックの最大段ボールを取得
        * 段ボールの数に応じて，場合わけ
        * 料金算出
        * */

        // トラックの最大段ボール数
        String sql = "SELECT MAX_BOX FROM TRUCK_CAPACITY ORDER BY MAX_BOX DESC";
        SqlParameterSource paramSource = new MapSqlParameterSource();
        List<Integer> TrackBox = parameterJdbcTemplate.queryForList(sql,paramSource,Integer.class);

        // 0が4トン，1が2トン
        // 0:200 1:80
        // 段ボール数からトラックの台数を算出する
        int fourTrack;
        int twoTrack;
        fourTrack = 0;
        twoTrack = 0;

        if(boxNum < TrackBox.get(1)){
            //段ボールの数が80個以下
            twoTrack = 1;
        } else if((boxNum < TrackBox.get(0)) && (boxNum >= TrackBox.get(1))){
            //段ボールの数が80個以上200個以下
            fourTrack = 1;
        } else if(boxNum > TrackBox.get(0)){
            // 段ボールの数が200個以
            //4トントラック(5万)：  段ボール/ 200
            fourTrack = boxNum / TrackBox.get(0);
            if((boxNum % TrackBox.get(0)) < TrackBox.get(1)) {
                twoTrack = 1;
            }else{
                fourTrack += 1;
            }
        }

        // 料金算出
        String sqlprice = "SELECT PRICE FROM TRUCK_CAPACITY ORDER BY MAX_BOX DESC";
        SqlParameterSource paramSourceprice = new MapSqlParameterSource();
        List<Integer> TrackPrice = parameterJdbcTemplate.queryForList(sqlprice,paramSourceprice,Integer.class);
        // 0が4トン，1が2トン
        int priceResult = fourTrack*TrackPrice.get(0) + twoTrack*TrackPrice.get(1);
        return priceResult;
    }

    /**
     * オプションサービスの料金を取得する。
     *
     * @param serviceId サービスID
     * @return 料金
     */
    public int getPricePerOptionalService(int serviceId) {
        String sql = "SELECT PRICE FROM OPTIONAL_SERVICE WHERE SERVICE_ID = :serviceId";

        SqlParameterSource paramSource = new MapSqlParameterSource("serviceId", serviceId);
        return parameterJdbcTemplate.queryForObject(sql, paramSource, Integer.class);
    }
}
