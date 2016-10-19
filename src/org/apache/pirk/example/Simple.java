package org.apache.pirk.example;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.pirk.encryption.Paillier;
import org.apache.pirk.querier.wideskies.Querier;
import org.apache.pirk.querier.wideskies.decrypt.DecryptResponse;
import org.apache.pirk.querier.wideskies.encrypt.EncryptQuery;
import org.apache.pirk.query.wideskies.Query;
import org.apache.pirk.query.wideskies.QueryInfo;
import org.apache.pirk.responder.wideskies.standalone.Responder;
import org.apache.pirk.response.wideskies.Response;
import org.apache.pirk.schema.data.DataSchemaLoader;
import org.apache.pirk.schema.data.DataSchemaRegistry;
import org.apache.pirk.schema.query.QuerySchema;
import org.apache.pirk.schema.query.QuerySchemaLoader;
import org.apache.pirk.schema.query.QuerySchemaRegistry;
import org.apache.pirk.schema.response.QueryResponseJSON;
import org.apache.pirk.utils.SystemConfiguration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Simple {

    private static int hashBitSize = 12;
    private static String hashKey = "my key";
    private static int dataPartitionBitSize = 8;
    private static int paillierBitSize = 384;
    private static int certainty = 128;

    private static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {

        /* The querier */
        
        ArrayList<String> selectors = new ArrayList<String>();
        selectors.add("Bob");
        selectors.add("Sam");

        try (InputStream is = new FileInputStream("dataschema.xml")) {
            DataSchemaRegistry.put(new DataSchemaLoader().loadSchema(is));
        }
        
        String queryType = null;
        try (InputStream is = new FileInputStream("queryschema.xml")) {
            QuerySchema qs = new QuerySchemaLoader().loadSchema(is);
            queryType = qs.getSchemaName();
            QuerySchemaRegistry.put(qs);
        }

        // Create the query & querier
        QueryInfo queryInfo = new QueryInfo(selectors.size(), hashBitSize, hashKey, dataPartitionBitSize,
                queryType, false, false, false);

        Paillier paillier = new Paillier(paillierBitSize, certainty);

        // The querier is kept secret by us in order to decrypt the results.
        Querier querier = new EncryptQuery(queryInfo, selectors, paillier).encrypt();

        // The query is sent to the responder to perform the search.
        Query query = querier.getQuery();

        debug(querier, "querier.json");
        debug(query, "query.json");

        // Assume we have passed the query to the responder to run...
        
        /* The responder, upon receiving the query. */
        Responder responder = new Responder(query);
        
        // The input data and output file.
        SystemConfiguration.setProperty("pir.inputData", "datafile.json");
        SystemConfiguration.setProperty("pir.outputFile", "resultfile.ser");
        responder.computeStandaloneResponse();

        // The response is still encrypted at this point.
        Response response = responder.getResponse();
        debug(response, "response.json");

        // Decrypt the response
        DecryptResponse decryptResponse = new DecryptResponse(response, querier);
        Map<String, List<QueryResponseJSON>> results = decryptResponse.decrypt();

        debug(results, "results.json");

        System.out.println("Finished");
    }

    private static void debug(Object obj, String fileName) throws IOException {
        (new File("debug")).mkdir();
        System.out.println("dumping " + obj);
        try (FileWriter io = new FileWriter("debug/" + fileName)) {
            io.write(gson.toJson(obj));
        }
    }
}
