package org.apache.pirk.example;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.pirk.encryption.Paillier;
import org.apache.pirk.querier.wideskies.Querier;
import org.apache.pirk.querier.wideskies.decrypt.DecryptResponse;
import org.apache.pirk.querier.wideskies.encrypt.EncryptQuery;
import org.apache.pirk.query.wideskies.Query;
import org.apache.pirk.query.wideskies.QueryInfo;
import org.apache.pirk.responder.wideskies.standalone.Responder;
import org.apache.pirk.response.wideskies.Response;
import org.apache.pirk.schema.data.LoadDataSchemas;
import org.apache.pirk.schema.query.LoadQuerySchemas;
import org.apache.pirk.utils.SystemConfiguration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Simple {

    private static String queryType = "simple query";
    private static double queryNumber = 1.0;
    private static String queryName = queryType + queryNumber;

    private static int keyedHashBitSize = 12;
    private static String hashedkey = "SomeKey";
    private static int dataPartitionBitSize = 8;
    private static int paillierBitSize = 384;
    private static int certainty = 128;

    private static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {

        ArrayList<String> selectors = new ArrayList<String>();
        selectors.add("Bob");

        SystemConfiguration.setProperty("data.schemas", "dataschema.xml");
        SystemConfiguration.setProperty("query.schemas", "queryschema.xml");
        LoadDataSchemas.initialize();
        LoadQuerySchemas.initialize();

        // Create the query & querier
        QueryInfo queryInfo = new QueryInfo(queryNumber, selectors.size(), keyedHashBitSize, hashedkey,
                dataPartitionBitSize, queryType, queryName, paillierBitSize, false, true, false);

        Paillier paillier = new Paillier(paillierBitSize, certainty);

        EncryptQuery encryptQuery = new EncryptQuery(queryInfo, selectors, paillier);
        encryptQuery.encrypt(Runtime.getRuntime().availableProcessors());

        Querier querier = encryptQuery.getQuerier();
        Query query = encryptQuery.getQuery();

        debug(querier, "querier.json");
        debug(query, "query.json");

        // Pass the query to the responder to run
        Responder responder = new Responder(query);
        SystemConfiguration.setProperty("pir.inputData", "datafile.json");
        SystemConfiguration.setProperty("pir.outputData", "resultfile.json");
        responder.computeStandaloneResponse();
        Response response = responder.getResponse();

        debug(responder, "responder.json");
        debug(responder.getResponse(), "response.json");

        // Decrypt the response
        DecryptResponse decryptResponse = new DecryptResponse(response, querier);
        decryptResponse.decrypt(Runtime.getRuntime().availableProcessors());

        debug(decryptResponse, "decryptResponse.json");

        System.out.println("Finished");
    }

    private static void debug(Object obj, String fileName) throws IOException {
        System.out.println("dumping " + obj);
        try (FileWriter io = new FileWriter(fileName)) {
            io.write(gson.toJson(obj));
        }
    }
}
