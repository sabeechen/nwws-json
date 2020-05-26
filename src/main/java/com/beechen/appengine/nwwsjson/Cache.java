package com.beechen.appengine.nwwsjson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;

public class Cache {
    HashMap<String, Entry> byStationProduct = new HashMap<>();
    Firestore db;
    CollectionReference dbCollection;

    public void warmup() throws InterruptedException, ExecutionException {
        FirestoreOptions firestoreOptions = FirestoreOptions.getDefaultInstance().toBuilder().setProjectId("nwws-oi")
                .build();
        db = firestoreOptions.getService();
        dbCollection = db.collection("messages");
        ApiFuture<QuerySnapshot> future = dbCollection.get();

        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        for (QueryDocumentSnapshot document : documents) {
            Entry entry = document.toObject(Entry.class);
            this.byStationProduct.put(entry.key(), entry);
        }
        System.out.println(String.format("Loaded %s entries", documents.size()));
    }

    public void add(Entry entry) {
        byStationProduct.put(entry.key(), entry);
        dbCollection.document(entry.key()).set(entry);
    }

    public Entry lookup(String station, String productID) {
        String key = station + productID;
        return byStationProduct.get(key);
    }

    public List<Entry> lookup(String center) {
        ArrayList<Entry> ret = new ArrayList<>();
        for(Entry value : this.byStationProduct.values()) {
            if (value.center.equals(center)) {
                ret.add(value);
            }
        }
        return ret;
    }

    public Set<String> stations() {
        Set<String> ret = new HashSet<>();
        for(Entry value : this.byStationProduct.values()) {
            ret.add(value.center);
        }
        return ret;
    }
}