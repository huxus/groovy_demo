

import com.intellij.database.model.DasTable
import com.intellij.database.util.Case
import com.intellij.database.util.DasUtil

/*
 * Available context bindings:
 *   SELECTION   Iterable<DasObject>
 *   PROJECT     project
 *   FILES       files helper
 */

// entity(dto)、mapper(dao) 与数据库表的对应关系在这里手动指明,idea Database 窗口里只能选下列配置了的 mapper
// tableName(key) : [mapper(dao),entity(dto)]
typeMapping = [
        (~/(?i)int|decimal|bigint/)              : "INTEGER",
        (~/(?i)float|double|real/)               : "DOUBLE",
        (~/(?i)date|time|datetime|timestamp/)    : "TIMESTAMP",
        (~/(?i)/)                                : "VARCHAR"
]

MapperBasePackage = "com.simu800.bo.dao.mapper" // 包名需手动填写
EntityBasePackage = "com.simu800.bo.entity" // 包名需手动填写

FILES.chooseDirectoryAndSave("Choose directory", "Choose where to store generated files") { dir ->
    SELECTION.filter { it instanceof DasTable }.each { generate(it, dir) }
}

def generate(table, dir) {
    def baseName = mapperName(table.getName(), true)
    def fields = calcFields(table)
    new File(dir, baseName + "Mapper.xml").withPrintWriter("utf-8") { out -> generate(table, out, baseName, fields) }
}

def generate(table, out, baseName, fields) {
    def str = baseName.substring(0, 1);
    def baseResultMap = str.toLowerCase() + baseName.substring(1) + "Map";
    def column = 'column'
    def date = new Date().format("yyyy/MM/dd")
    def tableName = table.getName()

    def dao = MapperBasePackage + ".${baseName}Mapper"
    def parameterType = EntityBasePackage + ".${baseName}Entity"

    out.println mappingsHead(dao)
    out.println resultMap(baseResultMap, parameterType, fields)
    out.println sql(fields, column)
    out.println tableNames(tableName)
    out.println orderBy(tableName)
    out.println condition(tableName, fields)
    out.println getCount(parameterType)
    out.println findByExample(tableName, fields, parameterType, column, baseResultMap)
    out.println findByPage(tableName, column, parameterType, baseResultMap)
    out.println insert(tableName, fields, parameterType)
    out.println deleteById(tableName, fields)
    out.println updateById(tableName, fields, parameterType)
    out.println mappingsEnd()

}

// ------------------------------------------------------------------------ mappingsHead
static def mappingsHead(mapper) {
    return '''<?xml version="1.0" encoding="UTF-8"?>
    <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
    <mapper namespace="''' + mapper + '''">
    '''
}

// ------------------------------------------------------------------------ resultMap
static def resultMap(baseResultMap, to, fields) {
    def inner = ''
    fields.each() {
        inner += '\t\t<result column="' + it.sqlFieldName + '" jdbcType="' + it.type + '" property="' + it.name + '"/>\n'
    }
    return '''\t<resultMap id="''' + baseResultMap + '''" type="''' + to + '''">
        <id column="id" jdbcType="INTEGER" property="id"/>
    ''' + inner + '''\t</resultMap>
    '''
}

// ------------------------------------------------------------------------ sql
static def sql(fields, column) {
    def str = '''\t<sql id="''' + column + '''">
        @inner@
    </sql> '''
    def inner = ''
    fields.each() {
        inner += ('\t\t' + it.sqlFieldName + ',\n')
    }
    return str.replace("@inner@", inner.substring(0, inner.length() - 2))
}

// ------------------------------------------------------------------------ tableNames
static def tableNames(tableName) {
    return '''\n\t<sql id="tableName">''' + tableName + '''</sql>\n'''
}

// ------------------------------------------------------------------------ orderBy
static def orderBy(tableName) {
    return '''\t<sql id="orderBy"> ORDER BY ''' + tableName +  '''.createTime desc </sql>'''
}

// ------------------------------------------------------------------------ condition
static def condition(tableName, fields) {
    return '''\n\t<sql id="condition">
        <if test="search != null">
            <where>''' + testNotNullStrSet2(fields) + '''
            </where>
        </if>
    </sql>'''
}

// ------------------------------------------------------------------------ getCount
static def getCount(parameterType) {
    return '''
    <select id="getCount" parameterType="''' + parameterType +'''" resultType="java.lang.Integer">
    \tselect count(*) from <include refid="tableName"/>
    \t<include refid="condition"/>
    </select>'''
}

// ------------------------------------------------------------------------ findByExample
static def findByExample(tableName, fields, parameterType, column, baseResultMap) {
    return '''
    <select id="findByExample" parameterType="''' + parameterType + '''" resultMap="''' + baseResultMap + '''">
        select
        <include refid="''' + column + '''"/>
        from ''' + tableName + '''
        <include refid="condition"/>
        <include refid="orderBy"/>
    </select>'''
}

// ------------------------------------------------------------------------ findByPage
static def findByPage(tableName, column, parameterType, baseResultMap) {
    return '''
    <select id="findByPage" resultMap="''' + baseResultMap + '''">
        select
        <include refid="''' + column + '''"/>
        from ''' + tableName + '''
        <include refid="condition"/>
        <choose>
            <when test="sort != null">
                order by #{sort}
            </when>
            <otherwise>
                order by createTime desc
            </otherwise>
        </choose>
        <if test="sort != null and order != null">
            #{order}
        </if>
        <if test="page != null and rows != null">
            limit #{page}, #{rows}
        </if>
    </select>'''
}

// ------------------------------------------------------------------------ insert
static def insert(tableName, fields, parameterType) {
    return '''
    <insert id="insert" parameterType="''' + parameterType + '''">
        insert into ''' + tableName + '''
        <trim prefix="(" suffix=")" suffixOverrides=",">  ''' + testNotNullStr(fields) + '''
        </trim>
        <trim prefix="values (" suffix=")" suffixOverrides=",">  ''' + testNotNullStrSet(fields) + '''
        </trim>
    </insert>'''
}

// ------------------------------------------------------------------------ deleteById
static def deleteById(tableName, fields) {
    return '''
    <delete id="deleteById" parameterType="java.lang.Integer">
        delete
        from ''' + tableName + '''
        where id = #{id}
    </delete>'''
}

// ------------------------------------------------------------------------ updateById
static def updateById(tableName, fields, parameterType) {
    return '''
    <update id="updateById" parameterType="''' + parameterType + '''">
        update ''' + tableName + '''
        <set> ''' + testNotNullStrWhere(fields) + '''
        </set>
        where id = #{id}
    </update>'''
}

// ------------------------------------------------------------------------ mappings
static def mappingsEnd() {
    return '''</mapper>'''
}



// ------------------------------------------------------------------------ start head
def calcFields(table) {
    DasUtil.getColumns(table).reduce([]) { fields, col ->
        def spec = Case.LOWER.apply(col.getDataType().getSpecification())
        def typeStr = typeMapping.find { p, t -> p.matcher(spec).find() }.value
        fields += [[
                           comment     : col.getComment(),
                           name        : mapperName(col.getName(), false),
                           sqlFieldName: col.getName(),
                           type        : typeStr,
                           annos       : ""]]
    }
}
def mapperName(str, capitalize) {
    def s = com.intellij.psi.codeStyle.NameUtil.splitNameIntoWords(str)
            .collect { Case.LOWER.apply(it).capitalize() }
            .join("")
            .replaceAll(/[^\p{javaJavaIdentifierPart}[_]]/, "_")
    name = capitalize || s.length() == 1 ? s : Case.LOWER.apply(s[0]) + s[1..-1]
}
// ------------------------------------------------------------------------ end head




static def testNotNullStrWhere(fields) {
    def inner = ''
    fields.each {
        inner += '''
        \t<if test="''' + it.name + ''' != null"> ''' + it.sqlFieldName + ''' = #{''' + it.name + '''}, </if> '''
    }
    return inner
}

static def selectListTestNotNullStrWhere(fields) {
    def inner = ''
    fields.each {
        inner += '''
        <if test="''' + it.name + ''' != null"> and ''' + it.sqlFieldName + ''' = #{''' + it.name + '''} </if> '''
    }
    return inner
}

static def testNotNullStrSet(fields) {
    def inner = ''
    fields.each {
        inner += '''
        \t<if test="''' + it.name + ''' != null"> #{''' + it.name + '''}, </if> '''
    }
    return inner
}

static def testNotNullStrSet2(fields) {
    def inner = ''
    fields.each {
        inner += '''
        \t\t<if test="search.''' + it.name + ''' != null"> and ''' + it.sqlFieldName + ''' = #{search.''' + it.name + '''} </if> '''
    }
    return inner
}

static def testNotNullStr(fields) {
    def inner1 = ''
    fields.each {
        inner1 += '''
        \t<if test = "''' + it.name + ''' != null" > ''' + it.sqlFieldName + ''', </if> '''
    }
    return inner1
}