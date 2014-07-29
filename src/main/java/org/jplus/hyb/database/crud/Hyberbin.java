/**
 * @date 2012/2/19 15:10 添加事务处理，重复使用的方法
 * @date 2012/4/13 14:10
 * @date 2012/8/6 添加延迟加载 自动关联查询 结果集从LinkedList改成ArrayList
 */
package org.jplus.hyb.database.crud;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import org.jplus.hyb.database.util.CacheFactory;
import org.jplus.hyb.database.bean.FieldColumn;
import org.jplus.hyb.database.bean.TableBean;
import org.jplus.hyb.database.config.ConfigCenter;
import org.jplus.hyb.database.transaction.IDbManager;
import org.jplus.hyb.database.util.GetSql;
import org.jplus.hyb.database.util.Pager;
import org.jplus.util.FieldUtil;
import org.jplus.util.NumberUtils;
import org.jplus.util.Reflections;

/**
 * 数据库持久层框架核心类之一 此类用于给定POJO类的数据库操作 强制绑定了数据库预处理机制。
 * 需要在项目缺省包下面添加配置文件properties(整个项目仅此一个数据库配置)。
 * 多用于对数据库单表的插入、更新、删除、查询操作，对POJO类的依赖性很强。 可以支持数据库事务处理。 支持一个数据库连接的单次，多次利用。
 *
 * @version 3.6
 * @param <T>
 * @author hyberbin
 */
public class Hyberbin<T> extends BaseDbTool{

    /** 表名 */
    private String tableName;
    /** 成员变量列表 */
    private List<FieldColumn> fields = null;
    /** 是否第一次自定义字段 */
    private boolean firstAddField = true;
    /** 表的实体类 */
    private T po;
    /** 是否更新空值 */
    private boolean updateNull = false;
    /** 为空的字段列表 */
    private List<FieldColumn> nuList;

    /**
     * 根据表的实体类初始化.
     * @param tablebean 表的实体类
     */
    private void ini(T po) {
        if (po != null) {
            try {
                TableBean tableBean = CacheFactory.MINSTANCE.getHyberbin(po.getClass());
                this.po = po;
                this.tableName = tableBean.getTableName();
                fields = new ArrayList<FieldColumn>(tableBean.getColumns());
            } catch (SecurityException ex) {
                log.error("初始化错误", ex);
            }
        }
    }

    private T getPo() {
        if (po == null) {
            log.error("po值为空，注意：空构造方法不能执行此操作");
        }
        return po;
    }

    /**
     * 构造方法,一次性操作。操作完后自动关闭数据库. Ex:News news=new News();//POJO类，源自于数据库
     * <strong>
     * Hyberbin hyberbin=new Hyberbin(news);
     * //构建Hyberbin,给出POJO类表示当前只对news表进行操作，而且是一次性操作。操作完后自动关闭数据库。
     * </strong>
     * @param tablebean 表的实体类
     */
    public Hyberbin(T tablebean) {
        super(ConfigCenter.INSTANCE.getManager());//数据库操作对象
        ini(tablebean);
    }

    public Hyberbin() {
        super(ConfigCenter.INSTANCE.getManager());//数据库操作对象
    }

    /**
     * 用自带的数据库连接进行数据库操作,用于构造可多次使用的Hyberbin.
     * <strong>本构造方法会自动开启事务.</strong>
     * @param tablebean 表的实体类
     * @param tx 数据库连接
     */
    public Hyberbin(T tablebean, IDbManager tx) {
        super(tx);//数据库操作对象
        ini(tablebean);
    }

    /**
     * 设置操作的表名. 如果没有设置将会默认采用实体名
     * @param tableName
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    /**
     * 移去一个成员变量 不需要更新或者读取的字段将不会出现在数据库操作当中.
     * 此方法对所有的查询、修改、添加都能起作用。用于在有很多字段情况下去除少量不需要操作的字段，提高数据库效率.
     * 些方法返回自身对象,可以链式编程。hyberbin.removeField("newstype").removeField("id"); Ex:
     * News news=new News();//POJO类，源自于数据库. Hyberbin hyberbin=new
     * Hyberbin(news);//构建Hyberbin,给出POJO类表示当前只对news表进行操作，而且是一次性操作。操作完后自动关闭数据库.
     * <strong><p>
     * hyberbin.removeField("newstype");</strong>
     * 如果还需要移除查其它字段可以继续removeField("id")等等. hyberbin.showAll();
     * @param fieldName 要移去的成员变量
     * @return 自身
     */
    public Hyberbin removeField(String fieldName) {
        log.trace("in removeField,fieldName{}", fieldName);
        if (fieldName == null || "".equals(fieldName.trim())) {
            return this;
        }
        FieldColumn fieldColumn = FieldUtil.getFieldColumnByCache(FieldUtil.getField(getPo().getClass(), fieldName));
        fields.remove(fieldColumn);
        log.debug("不查fieldName：{},column:{}", fieldName, fieldColumn.getColumn());
        return this;
    }

    /**
     * 设置一个成员变量 只有指定字段才出现在数据库操作当中.
     * 此方法对所有的查询、修改、添加都能起作用。用于在有很多字段情况下只对少量字段进行操作，提高数据库效率.
     * 些方法返回自身对象,可以链式编程。hyberbin.setField("newstype").setField("id"); Ex: News
     * news=new News();//POJO类，源自于数据库. Hyberbin hyberbin=new
     * Hyberbin(news);//构建Hyberbin,给出POJO类表示当前只对news表进行操作，而且是一次性操作。操作完后自动关闭数据库。
     * <strong><p>
     * hyberbin.setField("newstype");</strong>
     * 如果还需要查其它字段可以继续setField("id")等等.
     * hyberbin.showAll();//在这个查询到的集合中将只会查到“newstype”这个字段的信息.
     * @param fieldName 要查询的成员变量
     * @return 自身
     */
    public Hyberbin setField(String fieldName) {
        log.trace("in setField,fieldName:{}", fieldName);
        if (fields == null || firstAddField) {
            fields = new ArrayList<FieldColumn>(0);
            firstAddField = false;
        }
        FieldColumn fieldColumn = FieldUtil.getFieldColumnByCache(FieldUtil.getField(getPo().getClass(), fieldName));
        fields.add(fieldColumn);
        log.debug("加入fieldName:{},column:{}", fieldName, fieldColumn.getColumn());
        return this;
    }

    /**
     * 查询的时候获得所有字段名.
     * @return
     */
    private String getFieldList() {
        char[] quote = adapter.getQuote();
        if (fields != null) {
            StringBuilder fieldlist = new StringBuilder();
            for (FieldColumn field : fields) {
                if (!field.isIgnore()) {
                    fieldlist.append(",").append(quote[0]).append(field.getColumn()).append(quote[1]);
                }
            }
            return fieldlist.substring(1);
        } else {
            return "";
        }
    }

    /**
     * 从查询结果中取得数据存入表的实体类.
     * @param table 表的实体类
     * @param rs 查询结果
     */
    private Object loadData(Object table, ResultSet rs) throws Exception {
        log.trace("in loadData");
        for (FieldColumn fieldColumn : fields) {
            Field field = fieldColumn.getField();
            if (field.isAnnotationPresent(JoinColumn.class)) {//存在字段是外键的注解
                log.trace("has joinColumn field:{}", field.getName());
                Object newInstance = field.getType().newInstance();
                FieldColumn joinColumn = fieldColumn.cloneMe(FieldUtil.getField(field.getType(), Id.class));
                loadDataToPojo(newInstance, joinColumn, rs);
                FieldUtil.setFieldValue(table, field.getName(), newInstance);
                if (field.isAnnotationPresent(ManyToOne.class)) {//多对一
                    log.trace("manyToOne field:{}", field.getName());
                    ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
                    if (manyToOne.fetch() == FetchType.EAGER) {//如果不是延迟加载
                        log.trace("frtch is FetchType.EAGER");
                        new Hyberbin(newInstance).showOnebyKey(joinColumn.getField().getName());
                    }
                }//此处没有考虑一对一的...
            } else {
                loadDataToPojo(table, fieldColumn, rs);
            }
        }
        return table;
    }

    /**
     * 从查询结果中取得数据存入表的实体类.
     * @param table 表的实体类
     * @param rs 查询结果
     */
    private List loadListData(Object table, ResultSet rs) {
        log.trace("in loadListData");
        List list = new ArrayList(0);
        try {
            while (rs != null && rs.next()) {
                list.add(loadData(table.getClass().newInstance(), rs));
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("loadData error!", ex);
        }
        return list;
    }

    /**
     * 将数据库中取到的值存入POJO类.
     * @param table POJO类.
     * @param fieldColumn POJO类的字段.
     * @param rs 数据查询集合.
     */
    private void loadDataToPojo(Object table, FieldColumn fieldColumn, ResultSet rs) throws SQLException {
        log.trace("in loadDataToPojo ");
        Class type = fieldColumn.getField().getType();
        Object getResultSet = rs.getObject(fieldColumn.getColumn());
        if (getResultSet != null && !type.isAssignableFrom(getResultSet.getClass())) {
            getResultSet = rs.getObject(fieldColumn.getColumn(), type);
        }
        log.trace("loaded Data object:{},field:{},column:{},value:{}", table.getClass().getSimpleName(), fieldColumn.getField().getName(), fieldColumn.getColumn(), getResultSet);
        Reflections.invokeSetter(table, fieldColumn.getField().getName(), getResultSet, type);
    }

    /**
     * 根据PO中的信息自动拼接生成SQL语句.
     * @return
     */
    private GetSql getSql() {
        GetSql gs = new GetSql();
        for (FieldColumn field : fields) {
            Object value = field.getField().isAnnotationPresent(JoinColumn.class)
                    ? FieldUtil.getFatherFieldValue(getPo(), field.getField().getName())
                    : FieldUtil.getFieldValue(getPo(), field.getField().getName());
            String name = field.getColumn();
            if (value != null) {
                adapter.addParameter(value);
                gs.add(getQuotedItem(name), "?", "");//生成部分sql语句
                if (nuList != null && nuList.size() > 0) {
                    nuList.remove(field);
                }
            } else if (value == null && updateNull) {
                gs.add(getQuotedItem(name), "null", "");//生成部分sql语句
            }
        }
        if (nuList != null && nuList.size() > 0) {
            for (FieldColumn nullF : nuList) {
                if (fields.contains(nullF)) {
                    gs.add(getQuotedItem(nullF.getColumn()), "null", "");//生成部分sql语句
                }
            }
        }
        nuList = null;
        return gs;
    }

    /**
     * 将对象用标识符括起来
     * @param str
     * @return
     */
    private String getQuotedItem(String str) {
        char[] quote = adapter.getQuote();
        return quote[0] + str + quote[1];
    }

    /**
     * 设置是否更新NULL. 一般情况下系统会默认直接跳过值为空的字段，以提高数据库效率。 如果要清空该字段的信息就应该调用这个方法。
     * 此方法对数据库的插入、修改起作用。 Ex:
     * <strong><p>
     * hyberbin.setUpdateNull(true);</strong>
     * @param b true或者false
     */
    public void setUpdateNull(boolean b) {
        log.trace("in setUpdateNull");
        updateNull = b;
    }

    /**
     * 在预处理中加入新的参数. 此方法可以对数据库的增、删、改、查起作用.
     * 些方法返回自身对象,可以链式编程。hyberbin.addParameter("%"+other+"%").addParameter("1");
     * 为了增加数据库操作的安全性，建议所有自构的sql语句均要调用本方法。 加入参数时所有参数均不需要单引号. 加入时应该按照参数出现的位置按顺序调用.
     * like语句应该这样添加addParmeter("%"+other+"%"); Ex: News news=new
     * News();//POJO类，源自于数据库. Hyberbin hyberbin=new
     * Hyberbin(news);//构建Hyberbin,给出POJO类表示当前只对news表进行操作，而且是一次性操作。操作完后自动关闭数据库.
     * String other="hyb";
     * <strong><p>
     * hyberbin.addParameter("%"+other+"%");</strong>
     * <strong><p>
     * hyberbin.addParameter("1");</strong>
     * hyberbin.showOne("select * from news where content like ? and
     * newstype=?");
     * @param parmeter 参数
     * @return 自身对象
     */
    public Hyberbin addParmeter(Object parmeter) {
        log.trace("in addParmeter");
        adapter.addParameter(parmeter);
        return this;
    }

    /**
     * 数据库插入. 此方法用于对数据库单表的插入操作，默认情况下不插入字段值为空的字段. 此方法已经默认采用预处理技术.
     * 传入的参数是字段名而不是字段值. Ex: News news=new News();//POJO类，源自于数据库. Hyberbin
     * hyberbin=new
     * Hyberbin(news);//构建Hyberbin,给出POJO类表示当前只对news表进行操作，而且是一次性操作。操作完后自动关闭数据库。
     * news.setNewsType(2); news.setContent("这是一则新闻！");
     * <strong><p>
     * boolean
     * b=hyberbin.insert("id");//参数传入“id”表示id数据库会自动生成。如果不是自动生成那么可以传入""或者null。</strong>
     * @param primarkey 插入的主键，可以为空
     * @return 是否成功
     * @throws java.sql.SQLException
     */
    public int insert(String primarkey) throws SQLException {
        log.trace("in insert");
        removeField(primarkey);
        GetSql gs = getSql();
        String sql = gs.getInsert(tableName);//生成sql语句
        int update = adapter.update(createStatement(sql), sql);
        tx.closeConnection();
        return update;
    }

    /**
     * 数据库更新. 此方法用于对数据库单表的修改操作，默认情况下不修改字段值为空的字段. 此方法已经默认采用预处理技术.
     * 传入的参数是字段名而不是字段值. Ex: News news=new News();//POJO类，源自于数据库. Hyberbin
     * hyberbin=new
     * Hyberbin(news);//构建Hyberbin,给出POJO类表示当前只对news表进行操作，而且是一次性操作。操作完后自动关闭数据库.
     * news.setId(1); news.setNewsType(2); news.setContent("这是一则新闻！");
     * <strong><p>
     * boolean
     * b=hyberbin.updataByKey("id");//参数传入“id”表示根据id的值去修改，即修改id=1的那条记录.</strong>
     * @param key 更新条件字段
     * @return 是否成功
     * @throws java.sql.SQLException
     */
    public int updateByKey(String key) throws SQLException {
        log.trace("in updateByKey");
        removeField(key);
        FieldColumn fieldColumn = FieldUtil.getFieldColumnByCache(FieldUtil.getField(getPo().getClass(), key));
        GetSql gs = getSql();//自动生成sql语句
        Object PKvalue = FieldUtil.getFieldValue(getPo(), key);
        adapter.addParameter(PKvalue);
        gs.add(getQuotedItem(fieldColumn.getColumn()), "?", "where");//生成sql的where部分
        String sql = gs.getUpdate(tableName);//生成sql语句
        int update = adapter.update(createStatement(sql), sql);
        tx.closeConnection();
        return update;
    }

    /**
     * 给指定字段指定条件的语句自增. 此方法用于给指定字段指定条件的语句自动加1操作，例如用户看一则新闻后点击量自动加1;
     * 此方法默认不会使用sql预处理技术. 参数where中应该包含“where”关键字.
     * 建议用户自己使用预处理技术（addParameter("……")）以提高安全性. Ex: Hyberbin hyberbin=new
     * Hyberbin(news);//构建Hyberbin,给出POJO类表示当前只对news表进行操作，操作完后自动关闭数据库。
     * hyberbin.addParameter(1);
     * <strong><p>
     * hyberbin.autoUp("clickTimes","where id=?");</strong>
     * @param field 要自增的字段
     * @param where 条件 含有“where”
     * @return 是否成功
     * @throws java.sql.SQLException
     */
    public int autoUp(String field, String where) throws SQLException {
        log.trace("in autoUp");
        FieldColumn fieldColumn = FieldUtil.getFieldColumnByCache(FieldUtil.getField(getPo().getClass(), field));
        String sql = "update  " + getQuotedItem(tableName) + " set " + getQuotedItem(fieldColumn.getColumn()) + "=" + getQuotedItem(field) + "+1 " + where;
        int update = adapter.update(createStatement(sql), sql);
        tx.closeConnection();
        return update;
    }

    /**
     * 数据库删除. 此方法默认不会使用sql预处理技术. 参数where中应该包含“where”关键字.
     * 建议用户自己使用预处理技术（addParameter("……")）以提高安全性. Ex: Hyberbin hyberbin=new
     * Hyberbin(news);//构建Hyberbin,给出POJO类表示当前只对news表进行操作，操作完后自动关闭数据库。
     * hyberbin.addParameter(1);
     * <strong><p>
     * hyberbin.dell("where id=?");//删除id为1的新闻</strong>
     * @param where 要删除的条件 含有“where”
     * @return 是否成功
     * @throws java.sql.SQLException
     */
    public int delete(String where) throws SQLException {
        log.trace("in delete");
        String sql = "delete from " + getQuotedItem(tableName) + " " + where;
        int update = adapter.update(createStatement(sql), sql);
        tx.closeConnection();
        return update;
    }

    /**
     * 根据键值删除一条数据. 此方法默认使用sql预处理技术. 用户只需要提供关键字段的字段名即可。注意：参数是字段名. Ex: Hyberbin
     * hyberbin=new
     * Hyberbin(news);//构建Hyberbin,给出POJO类表示当前只对news表进行操作，操作完后自动关闭数据库.
     * hyberbin.addParameter(1);
     * <strong><p>
     * hyberbin.dellOneByKey("id");//删除id为1的新闻</strong>
     * @param key 键值
     * @return 是否成功
     * @throws java.sql.SQLException
     */
    public int deleteByKey(String key) throws SQLException {
        log.trace("in deleteByKey");
        FieldColumn fieldColumn = FieldUtil.getFieldColumnByCache(FieldUtil.getField(getPo().getClass(), key));
        String sql = "delete from " + getQuotedItem(tableName) + " where " + fieldColumn.getColumn() + " =?";
        Object PKvalue = FieldUtil.getFieldValue(getPo(), key);
        adapter.addParameter(PKvalue);
        int update = adapter.update(createStatement(sql), sql);
        tx.closeConnection();
        return update;
    }

    /**
     * 根据指定SQL语句查询一条数据. 此方法用于对数据库单表的查询操作，用于用户自己提供sql语句.
     * 此方法返回的是Object对象，用户需要自行强制类型转换. 此方法默认不会使用sql预处理技术.
     * 建议用户自己使用预处理技术（addParameter("……")）以提高安全性. Ex: Hyberbin hyberbin=new
     * Hyberbin(news);//构建Hyberbin,给出POJO类表示当前只对news表进行操作，操作完后自动关闭数据库.
     * hyberbin.addParameter(2);
     * <strong><p>
     * hyberbin.showOne("select * from news where id=?"); //查询id为2的新闻</strong>
     * @param sql 指定SQL语句
     * @return 查询对象
     * @throws java.sql.SQLException
     */
    public T showOne(String sql) throws SQLException {
        log.trace("in showOne");
        showOneWithoutTx(sql);
        tx.closeConnection();
        return getPo();
    }

    private T showOneWithoutTx(String sql) throws SQLException {
        log.trace("in showOneWithoutTx");
        ResultSet rs = adapter.findSingle(createStatement(sql), sql);//执行查询
        try {
            if (rs != null && rs.next()) {
                loadData(getPo(), rs);
            } else {
                po = (T) getPo().getClass().newInstance();//创建实体
            }
        } catch (Exception ex) {
            if (ex instanceof SQLException) {
                throw (SQLException) ex;
            } else {
                throw new IllegalArgumentException("loadData error!", ex);
            }
        }
        return getPo();
    }

    /**
     * 通过指定字段查询一条数据. 此方法用于对数据库单表的查询操作，用于只提供关键的字段名信息.
     * 此方法返回的是Object对象，用户需要自行强制类型转换. 此方法默认使用sql预处理技术. Ex: news.setId(2);
     * Hyberbin hyberbin=new
     * Hyberbin(news);//构建Hyberbin,给出POJO类表示当前只对news表进行操作，操作完后自动关闭数据库.
     * <strong><p>
     * hyberbin.showOnebyKey("id"); //查询id为2的新闻.</strong>
     * 如果有缓存会自动从缓存中取出对象并克隆到tablebean.
     * @param key
     * @return 查询对象
     * @throws java.sql.SQLException
     */
    public T showOnebyKey(String key) throws SQLException {
        log.trace("in showOnebyKey");
        Object value = FieldUtil.getFieldValue(getPo(), key);
        FieldColumn fieldColumn = FieldUtil.getFieldColumnByCache(FieldUtil.getField(getPo().getClass(), key));
        adapter.addParameter(value);
        String sql = "select " + getFieldList() + " from " + getQuotedItem(tableName) + " where " + fieldColumn.getColumn() + "=?";
        showOneWithoutTx(sql);
        tx.closeConnection();
        return getPo();
    }

    /**
     * 数据库批量查询. 此方法查询一个表的所有记录，不常用. 此方法返回的是 List，用户需要自行强制类型转换. Ex: Hyberbin
     * hyberbin=new Hyberbin(news);
     * <strong><p>
     * hyberbin.showAll();</strong>
     * @return 查询结果集合
     * @throws java.sql.SQLException
     */
    public List<T> showAll() throws SQLException {
        log.trace("in showAll");
        String sql = "select " + getFieldList() + " from " + tableName;
        List showListWithoutTx = showListWithoutTx(sql);
        tx.closeConnection();
        return showListWithoutTx;
    }

    /**
     * 数据库批量查询. 此方法查询一个表的所有记录，不常用. 此方法返回的是 List，用户需要自行强制类型转换. Ex: Hyberbin
     * hyberbin=new Hyberbin(news);
     * <strong><p>
     * hyberbin.showAll();</strong>
     * @return 查询结果集合
     * @throws java.sql.SQLException
     */
    public List<T> showAll(String where) throws SQLException {
        log.trace("in showAll (String where) ");
        String sql = "select " + getFieldList() + " from " + tableName+" "+where;
        List showListWithoutTx = showListWithoutTx(sql);
        tx.closeConnection();
        return showListWithoutTx;
    }

    /**
     * 查询数据库中符合条件的记录数. 此方法用于查询符合用户条件的数据记录条数. 参数where中应该包含“where”关键字.
     * 此方法默认不使用sql预处理技术. 建议用户自己使用预处理技术（addParameter("……")）以提高安全性. Ex: Hyberbin
     * hyberbin=new
     * Hyberbin(news);//构建Hyberbin,给出POJO类表示当前只对news表进行操作，操作完后自动关闭数据库.
     * hyberbin.addParameter(2);
     * <strong><p>
     * hyberbin.getCount("where newstype=?"); //查询newstype为2的新闻有多少条</strong>
     * <strong><p>
     * hyberbin.getCount(""); //查询news表的所有</strong>
     * @param sql
     * @return
     * @throws java.sql.SQLException
     * @
     */
    public int getCount(String sql) throws SQLException {
        log.trace("in getCount");
        sql = "select count(*) from (" + sql + ") as count";
        Object findUnique = adapter.findUnique(createStatement(sql), sql);
        tx.closeConnection();
        return NumberUtils.parseInt(findUnique);
    }

    /**
     * 分页查询. 参数where中应该包含“where”关键字. 此方法默认不使用sql预处理技术.
     * 建议用户自己使用预处理技术（addParameter("……")）以提高安全性. Ex: Pagger pagger=new
     * Pagger(10);//页面大小为10。 Hyberbin hyberbin=new
     * Hyberbin(news);//构建Hyberbin,给出POJO类表示当前只对news表进行操作，操作完后自动关闭数据库.
     * hyberbin.addParameter(2); <strong>
     * hyberbin.showByMySqlPage("where newstype=?",pagger);</strong>
     * @param where 查询条件 含有“where”.
     * @param pager 分页对象
     * @throws java.sql.SQLException
     */
    public void showByPage(String where, Pager pager) throws SQLException {
        log.trace("in showByPage");
        String sql = "select " + getFieldList() + "  from " + getQuotedItem(tableName) + " " + where;
        ResultSet rs = adapter.findPageList(createStatement(sql), pager, sql);
        List list = loadListData(getPo(), rs);
        sql = "select count(*) from (" + sql + ") as count";
        Object findUnique = adapter.findUnique(createStatement(sql), sql);
        pager.setItems(NumberUtils.parseInt(findUnique));
        pager.setData(list);
        tx.closeConnection();
    }

    /**
     * 数据库的批量查询. 此方法用于对数据库单表的查询操作，用于用户自己提供sql语句. 由于是自己构造sql语句所以适用于所有数据库.
     * 此方法默认不会使用sql预处理技术. 建议用户自己使用预处理技术（addParameter("……")）以提高安全性. Ex: Hyberbin
     * hyberbin=new
     * Hyberbin(news);//构建Hyberbin,给出POJO类表示当前只对news表进行操作，而且是一次性操作。操作完后自动关闭数据库.
     * hyberbin.addParameter(2);
     * <strong><p>
     * hyberbin.showList("select * from news where newstype=?");</strong>
     * @param sql
     * @return 查询结果集合
     * @throws java.sql.SQLException
     */
    public List<T> showList(String sql) throws SQLException {
        log.trace("in showList");
        List showListWithoutTx = showListWithoutTx(sql);
        tx.closeConnection();
        return showListWithoutTx;
    }

    private List<T> showListWithoutTx(String sql) throws SQLException {
        log.trace("in showListWithoutTx");
        ResultSet rs = adapter.findList(createStatement(sql), sql);//执行查询
        return loadListData(getPo(), rs);
    }

    private List<Map> getMapList(ResultSet rs) throws SQLException {
        List<Map> list = new ArrayList<Map>();
        if (rs != null) {
            ResultSetMetaData metaData;
            metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            while (rs.next()) {
                Map map = new MyMap();
                for (int i = 1; i <= columnCount; i++) {
                    map.put(metaData.getColumnName(i), rs.getObject(i));
                }
                list.add(map);
            }
        }
        return list;
    }

    public List<Map> getMapList(String sql) throws SQLException {
        List<Map> mapList = getMapList(adapter.findList(createStatement(sql), sql));
        tx.closeConnection();
        return mapList;
    }

    /**
     * 根据SQL语句查出一个map集合.
     * @param sql SQL语句
     * @param pager 分页对象
     * @throws java.sql.SQLException
     */
    public void getMapList(String sql, Pager pager) throws SQLException {
        ResultSet findPageList = adapter.findPageList(createStatement(sql), pager, sql);
        pager.setData(getMapList(findPageList));
        sql = "select count(*) from (" + sql + ") as count";
        Object findUnique = adapter.findUnique(createStatement(sql), sql);
        pager.setItems(NumberUtils.parseInt(findUnique));
        tx.closeConnection();
    }

    /**
     * 获得将要设置为空的字段
     * @return
     */
    public List<FieldColumn> getNuList() {
        log.trace("in getNuList");
        return nuList;
    }

    /**
     * 添加一个要设置为空的字段
     * @param field
     */
    public void addNullField(String field) {
        log.trace("in addNullField");
        if (firstAddField) {
            firstAddField = false;
            nuList = new ArrayList<FieldColumn>();
        }
        nuList.add(FieldUtil.getFieldColumnByCache(FieldUtil.getField(getPo().getClass(), field)));
    }

}

class MyMap extends HashMap<String, Object> {

    @Override
    public Object put(String key, Object value) {
        return super.put(key.toLowerCase(), value);
    }

    @Override
    public Object get(Object key) {
        return super.get((key + "").toLowerCase());
    }

}