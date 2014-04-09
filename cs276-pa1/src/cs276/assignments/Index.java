package cs276.assignments;

import cs276.util.Pair;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.*;

// custom
import cs276.util.TermDocComparator;

public class Index {

	// Term id -> (position in index file, doc frequency) dictionary
	private static Map<Integer, Pair<Long, Integer>> postingDict 
		= new TreeMap<Integer, Pair<Long, Integer>>();
	// Doc name -> doc id dictionary
	private static Map<String, Integer> docDict
		= new TreeMap<String, Integer>();
	// Term -> term id dictionary
	private static Map<String, Integer> termDict
		= new TreeMap<String, Integer>();
	// Block queue
	private static LinkedList<File> blockQueue
		= new LinkedList<File>();

	// Total file counter
	private static int totalFileCount = 0;
	// Document counter
	private static int docIdCounter = 0;
	// Term counter
	private static int wordIdCounter = 0;
	// Index
	private static BaseIndex index = null;

	
	/* 
	 * Write a posting list to the file 
	 * You should record the file position of this posting list
	 * so that you can read it back during retrieval
	 * 
	 * */
	private static void writePosting(FileChannel fc, PostingList posting)
			throws IOException {
		/*
		 * Your code here
		 */
	}

	public static void main(String[] args) throws IOException {
		/* Parse command line */
		if (args.length != 3) {
			System.err
					.println("Usage: java Index [Basic|VB|Gamma] data_dir output_dir");
			return;
		}

		/* Get index */
		String className = "cs276.assignments." + args[0] + "Index";
		try {
			Class<?> indexClass = Class.forName(className);
			index = (BaseIndex) indexClass.newInstance();
		} catch (Exception e) {
			System.err
					.println("Index method must be \"Basic\", \"VB\", or \"Gamma\"");
			throw new RuntimeException(e);
		}

		/* Get root directory */
		String root = args[1];
		File rootdir = new File(root);
		if (!rootdir.exists() || !rootdir.isDirectory()) {
			System.err.println("Invalid data directory: " + root);
			return;
		}

		/* Get output directory */
		String output = args[2];
		File outdir = new File(output);
		if (outdir.exists() && !outdir.isDirectory()) {
			System.err.println("Invalid output directory: " + output);
			return;
		}

		if (!outdir.exists()) {
			if (!outdir.mkdirs()) {
				System.err.println("Create output directory failure");
				return;
			}
		}

		/* BSBI indexing algorithm */
		File[] dirlist = rootdir.listFiles();

        // use ArrayList to collect all termID-docID pairs
        List<Pair<Integer, Integer>> pairs = new ArrayList<Pair<Integer, Integer>>();

		/* For each block */
		for (File block : dirlist) {
			File blockFile = new File(output, block.getName());
			blockQueue.add(blockFile);

			File blockDir = new File(root, block.getName());
			File[] filelist = blockDir.listFiles();
			
			/* For each file */
			for (File file : filelist) {
				++totalFileCount;
				String fileName = block.getName() + "/" + file.getName();
                // use pre-increment to ensure docID > 0
                int docID = ++docIdCounter;
				docDict.put(fileName, docID);

				BufferedReader reader = new BufferedReader(new FileReader(file));
				String line;
				while ((line = reader.readLine()) != null) {
					String[] tokens = line.trim().split("\\s+");
					for (String token : tokens) {
						/*
						 * lookup/create term id
						 * accumulate <termId, docId>
						 */

                        int termID;
                        // if termDict contains the token already, do nothing
                        // else insert it and get new termID
                        if (!termDict.containsKey(token)) {
                            // use pre-increment to ensure termID > 0
                            termID = ++wordIdCounter;
                            termDict.put(token, termID);
                        } else {
                            termID = termDict.get(token);
                        }

                        // add termID-docID into pairs
                        pairs.add(new Pair(termID, docID));
					}
				}
				reader.close();
			}

			/* Sort and output */
			if (!blockFile.createNewFile()) {
				System.err.println("Create new block failure.");
				return;
			}
			
			RandomAccessFile bfc = new RandomAccessFile(blockFile, "rw");
			
            // sort pairs
            Collections.sort(pairs, new TermDocComparator());

            // write output
            int cnt = 0, prevTermID = -1, termID, prevDocID = -1, docID;
            if (pairs.size() > 0)
                // set valid prevTermID
                prevTermID = pairs.get(0).getFirst();

            List<Integer> postings = new ArrayList<Integer>();
            for (Pair<Integer, Integer> p : pairs) {
                termID = p.getFirst();
                docID = p.getSecond();

                if (termID == prevTermID) {
                    // duplicate docIDs only added once
                    if (prevDocID != docID) {
                        postings.add(docID);
                    }
                    prevDocID = docID;
                } else {
                    // a different term is encountered
                    // should write postings of previous term to disk
                    index.writePosting(bfc.getChannel(), new PostingList(prevTermID, postings));

                    // start new postings
                    postings.clear();
                    postings.add(docID);
                    prevTermID = termID;
                    prevDocID = -1;
                }
            }

			bfc.close();
		}

		/* Required: output total number of files. */
		System.out.println(totalFileCount);

		/* Merge blocks */
		while (true) {
			if (blockQueue.size() <= 1)
				break;

			File b1 = blockQueue.removeFirst();
			File b2 = blockQueue.removeFirst();
			
			File combfile = new File(output, b1.getName() + "+" + b2.getName());
			if (!combfile.createNewFile()) {
				System.err.println("Create new block failure.");
				return;
			}

			RandomAccessFile bf1 = new RandomAccessFile(b1, "r");
			RandomAccessFile bf2 = new RandomAccessFile(b2, "r");
			RandomAccessFile mf = new RandomAccessFile(combfile, "rw");
			 
			/*
			 * merge two PostingList
			 */
            FileChannel fc1 = bf1.getChannel();
            FileChannel fc2 = bf2.getChannel();
            FileChannel mfc = mf.getChannel();

            PostingList p1 = index.readPosting(fc1);
            PostingList p2 = index.readPosting(fc2);

            while (p1 != null && p2 != null) {
                int t1 = p1.getTermId();
                int t2 = p2.getTermId();

                if (t1 == t2) {
                    // merge postings of the same term
                    List<Integer> p3 = new ArrayList<Integer>();
                    Iterator<Integer> iter1 = p1.getList().iterator();
                    Iterator<Integer> iter2 = p2.getList().iterator();
                    int docID1, docID2;

                    if (iter1.hasNext() && iter2.hasNext()) {
                        docID1 = iter1.next();
                        docID2 = iter2.next();

                        while (true) {
                            // removed duplicates in postings list
                            // TODO: need to consider docID1 == docID2 case
                            if (docID1 < docID2) {
                                p3.add(docID1);
                                if (iter1.hasNext()) {
                                    docID1 = iter1.next();
                                } else {
                                    break;
                                }
                            } else {
                                p3.add(docID2);
                                if (iter2.hasNext()) {
                                    docID2 = iter2.next();
                                } else {
                                    break;
                                }
                            }
                        }
                    }

                    // add remaining of list 1
                    while (iter1.hasNext()) {
                        p3.add(iter1.next());
                    }
                    // ditto
                    while (iter2.hasNext()) {
                        p3.add(iter2.next());
                    }

                    // write p3 to disk
                    index.writePosting(mfc, new PostingList(t1, p3));
                    p1 = index.readPosting(fc1);
                    p2 = index.readPosting(fc2);
                } else if (t1 < t2) {
                    // write p1
                    index.writePosting(mfc, p1);
                    p1 = index.readPosting(fc1);
                } else {
                    // write p2
                    index.writePosting(mfc, p2);
                    p2 = index.readPosting(fc2);
                }
            }

			bf1.close();
			bf2.close();
			mf.close();
			b1.delete();
			b2.delete();
			blockQueue.add(combfile);
		}

		/* Dump constructed index back into file system */
		File indexFile = blockQueue.removeFirst();
		indexFile.renameTo(new File(output, "corpus.index"));

		BufferedWriter termWriter = new BufferedWriter(new FileWriter(new File(
				output, "term.dict")));
		for (String term : termDict.keySet()) {
			termWriter.write(term + "\t" + termDict.get(term) + "\n");
		}
		termWriter.close();

		BufferedWriter docWriter = new BufferedWriter(new FileWriter(new File(
				output, "doc.dict")));
		for (String doc : docDict.keySet()) {
			docWriter.write(doc + "\t" + docDict.get(doc) + "\n");
		}
		docWriter.close();

		BufferedWriter postWriter = new BufferedWriter(new FileWriter(new File(
				output, "posting.dict")));
		for (Integer termId : postingDict.keySet()) {
			postWriter.write(termId + "\t" + postingDict.get(termId).getFirst()
					+ "\t" + postingDict.get(termId).getSecond() + "\n");
		}
		postWriter.close();
	}

}
