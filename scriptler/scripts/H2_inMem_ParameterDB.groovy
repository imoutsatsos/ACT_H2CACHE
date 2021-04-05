/*** BEGIN META {
  "name" : "H2_inMem_ParameterDB",
  "comment" : "Creates (if it does not exist) and initializes an in memory H2 database for caching job parameters",
  "parameters" : ['vHELPER_DB','vSTOREDPARAMS'],
  "core": "1.596",
  "authors" : [
    { name : "Ioannis K. Moutsatsos" }
  ]
} END META**/

/**
 * Last Update FEB-01-2019
 * DB Name is session specific so DB can be cleaned up safely
 */


import org.h2.Driver
import groovy.sql.Sql
import org.kohsuke.stapler.Stapler
import java.util.logging.Level; 
import java.util.logging.Logger;
import groovy.json.*
def slurper = new groovy.json.JsonSlurper()  

def LOGGER = Logger.getLogger("org.biouno.AC_Scripts"); 
def sessionID= Stapler.getCurrentRequest().getSession().getId() 
def tableName= "PARAMS_$sessionID"
LOGGER.log(Level.INFO, "SessionParameterTable (name): $tableName"); 

/*default options for CSVWRITE H2 function */
def dd = "fieldDelimiter="
def cs="caseSensitiveColumnNames=true"

stmt1= "CREATE TABLE IF NOT EXISTS $tableName (PARAM VARCHAR PRIMARY KEY, PARAMVALUE VARCHAR DEFAULT '')"
stmt2= "MERGE INTO $tableName KEY(PARAM) VALUES('SESSION_$sessionID', '$sessionID')" as String
stmt3= "MERGE INTO $tableName KEY(PARAM) VALUES('LASTUPDATE_$sessionID', '${new Date().format('yyyy-MM-dd-HH:mm:ss').toString()}')" as String

def stmtP=''
def paramsOfInterest=vSTOREDPARAMS.split(',').collect{it.trim()}
LOGGER.log(Level.INFO, "Try Making.DB (name): $vHELPER_DB"); 
try{
populator=new DatabasePopulator(vHELPER_DB as String)
}catch(Exception e){
LOGGER.log(Level.INFO, "FAILED Making.DB (name): $vHELPER_DB"+"\n$e"); 
}

LOGGER.log(Level.INFO, "Success Making.DB (name): $vHELPER_DB"); 
LOGGER.log(Level.INFO, "SessionParameterTable (name): $tableName"); 
/*
 We add the required scripts in the order we want them executed
 */
populator.addScript(stmt1)
populator.addScript(stmt2)
populator.addScript(stmt3)

/*
now store all the parameters in the current binding
JSON parameters names are suffixed by a timestamp and 
stored as unique records

*/
LOGGER.log(Level.INFO, "OfInterest: $paramsOfInterest"); 
d = new Date() 
tstamp= d.format('yyyyMMddhhmmss')  
binding.variables.each{
  if (paramsOfInterest.contains(it.key as String)){
  LOGGER.log(Level.INFO,"BINDING VAR: ${it.key} MATCHES IN ${paramsOfInterest}}")
  def suffix=''
	  if(it.key.startsWith('JSON') && !(it.value==null ||it.value=='')){
        tstamp=slurper.parseText(it.value).TSTAMP

	  suffix= tstamp 
	  }else{
	  suffix=sessionID
	  }

  stmtP= "MERGE INTO $tableName KEY(PARAM) VALUES('${it.key}_$suffix', '${it.value}')" as String
  populator.addScript(stmtP)  
  LOGGER.log(Level.INFO,"STORING in $tableName:${it.key}_$suffix:${it.value}")  
  }
   //LOGGER.log(Level.INFO,"BINDING VAR: ${it.key} NOT IN ${paramsOfInterest}}")  
}

/*
..and now we execute them
 */
try {
populator.populate()
LOGGER.log(Level.INFO,"SUCCESS: PARAMETER OFINT STORING in $tableName")
test="<input name=\"value\" value=\"${tableName}\" class=\"setting-input\" type=\"text\">"
LOGGER.log(Level.INFO,"SUCCESS-Returning input: $test")
return """<input name="value" value="${tableName}" class="setting-input" type="text">"""
 }catch(Exception e){
return """<input name="value" value="${e}" class="setting-input" type="text">"""
 }

class DatabaseFactory{
    private type, name
    public DatabaseFactory(){

    }
    public build(name='builddb'){
        def LOGGER = Logger.getLogger("org.biouno.AC_Scripts"); 
        /* Create an h2 in-memory db, calling it what you like, here db1 */
        def db = Sql.newInstance("jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1", "org.h2.Driver")
        LOGGER.log(Level.INFO,"SUCCESS-build: $name")
        return db
    }
}
/**
 * Populates, initializes, or cleans up a database using SQL scripts defined in
 * string scripts.
 */
class DatabasePopulator {
    private List<String> scripts = new ArrayList<>();
    private separator=';', commentPrefix='--'
    private db
    def LOGGER = Logger.getLogger("org.biouno.AC_Scripts"); 

    public DatabasePopulator() {
        def dbFactory= new DatabaseFactory()
        this.db=dbFactory.build()
    }
  
      public DatabasePopulator(String dbName) {
        def dbFactory= new DatabaseFactory()
        this.db=dbFactory.build(dbName)
    }
    /**
     * Add default SQL scripts to execute to populate the database.
     * <p>The default scripts are {@code "schema.sql"} to create the database
     * schema and {@code "data.sql"} to populate the database with data.
     * @return {@code this}, to facilitate method chaining
     */
    public addDefaultScripts() {
        return addScripts("schema.sql", "data.sql");
    }

    /**
     * Add multiple SQL scripts to execute to initialize or populate the database.
     * @param scripts the scripts to execute
     * @return {@code this}, to facilitate method chaining
     * @since 4.0.3
     */
    public addScripts(String[] scripts) {
        scripts.each {
            addScript(it)
        }
        return this;
    }
    /**
     * Add a script to execute to initialize or clean up the database.
     * @param script the path to an SQL script (never {@code null})
     */
    public void addScript(String script) {
        assert script!=null
        this.scripts.add(script);
    }

    public void populate(){
        scripts.each{
                try{
            this.db.execute(it)
            LOGGER.log(Level.INFO, "SUCCESS executing:\n $it") 
            }catch(Exception e){
            LOGGER.log(Level.INFO, "FAILED executing:\n $it"+"\n$e") 
            }
        }
    }

}
