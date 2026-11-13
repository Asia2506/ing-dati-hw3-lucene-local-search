package it.uniroma3.IngDati.hw3;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import java.nio.file.Paths;

public class IndexInspector {
    public static void main(String[] args) {
        try {
            // Assicurati che il percorso corrisponda a quello usato nel tuo Indexer
            Directory dir = FSDirectory.open(Paths.get("index_dir_Nuovo"));
            IndexReader reader = DirectoryReader.open(dir);

            System.out.println("=== ISPEZIONE COMPLETA DELL'INDICE (Campo: filename) ===");

            // Iteriamo su tutti i segmenti dell'indice
            for (LeafReaderContext context : reader.leaves()) {
                LeafReader leafReader = context.reader();
                Terms terms = leafReader.terms("filename"); // Prendiamo le parole del campo filename

                if (terms != null) {
                    TermsEnum termsEnum = terms.iterator();
                    BytesRef term;
                    
                    // Stampiamo TUTTI i termini senza nessun filtro
                    while ((term = termsEnum.next()) != null) {
                        String word = term.utf8ToString();
                        int docFreq = termsEnum.docFreq();
                        System.out.println("Token: '" + word + "' | Documenti che lo contengono: " + docFreq);
                    }
                }
            }

            reader.close();
            dir.close();
            System.out.println("=== FINE ISPEZIONE ===");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}