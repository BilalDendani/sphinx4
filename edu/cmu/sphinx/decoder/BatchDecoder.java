/*
 * Copyright 1999-2002 Carnegie Mellon University.  
 * Portions Copyright 2002 Sun Microsystems, Inc.  
 * Portions Copyright 2002 Mitsubishi Electronic Research Laboratories.
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL 
 * WARRANTIES.
 *
 */

package edu.cmu.sphinx.decoder;

import edu.cmu.sphinx.frontend.util.StreamAudioSource;
import edu.cmu.sphinx.frontend.util.StreamCepstrumSource;
import edu.cmu.sphinx.frontend.DataSource;

import edu.cmu.sphinx.util.SphinxProperties;
import edu.cmu.sphinx.util.Timer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.IOException;

import java.net.URL;

import java.util.StringTokenizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;


/**
 * Decodes a batch file containing a list of files to decode.
 * The files can be either audio files or cepstral files, but defaults
 * to audio files. To decode cepstral files, set the Sphinx property
 * <code> edu.cmu.sphinx.decoder.BatchDecoder.inputDataType = cepstrum </code>
 */
public class BatchDecoder {

    private final static String PROP_PREFIX = 
	"edu.cmu.sphinx.decoder.BatchDecoder.";

    /**
     * The SphinxProperty name for how many files to skip.
     */
    public final static String PROP_SKIP = PROP_PREFIX + "skip";
    public final static String PROP_WHICH_BATCH = PROP_PREFIX + "whichBatch";
    public final static String PROP_TOTAL_BATCHES 
	= PROP_PREFIX + "totalBatches";

    /**
     * The SphinxProperty name for the input data type.
     */
    public final static String PROP_INPUT_TYPE = PROP_PREFIX+"inputDataType";

    private DataSource dataSource;
    private Decoder decoder;
    private String batchFile;
    private String context;
    private String inputDataType;
    private int skip;
    private int whichBatch;
    private int totalBatches;


    /**
     * Constructs a BatchDecoder.
     *
     * @param context the context of this BatchDecoder
     * @param batchFile the file that contains a list of files to decode
     */
    public BatchDecoder(String context, String batchFile) throws IOException {
	this.context = context;

	SphinxProperties props = SphinxProperties.getSphinxProperties(context);
	inputDataType = props.getString(PROP_INPUT_TYPE, "audio");
	skip = props.getInt(PROP_SKIP, 0);
	whichBatch = props.getInt(PROP_WHICH_BATCH, 0);
	totalBatches = props.getInt(PROP_TOTAL_BATCHES, 1);

	if (inputDataType.equals("audio")) {
	    dataSource = new StreamAudioSource
		("batchAudioSource", context, null, null);
	} else if (inputDataType.equals("cepstrum")) {
	    dataSource = new StreamCepstrumSource
		("batchCepstrumSource", context);
	} else {
	    throw new Error("Unsupported data type: " + inputDataType + "\n" +
			    "Only audio and cepstrum are supported\n");
	}

	decoder = new Decoder(context, dataSource);

        this.batchFile = batchFile;
    }


    /**
     * Decodes the batch of audio files
     */
    public void decode() throws IOException {

	int curCount = skip;
        System.out.println("\nBatchDecoder: decoding files in " + batchFile);
        System.out.println("----------");

	for (Iterator i = getLines(batchFile).iterator(); i.hasNext();) {
	    String line = (String) i.next();
	    StringTokenizer st = new StringTokenizer(line);
	    String ref = null;
	    String file = (String) st.nextToken();
	    StringBuffer reference = new StringBuffer();

	    while (st.hasMoreTokens()) {
		reference.append((String) st.nextToken());
		reference.append(" ");
	    }

	    if (reference.length() > 0) {
		ref = reference.toString();
	    }
	    if (++curCount >= skip) {
		curCount = 0;
		decodeFile(file, ref);
	    }
        }

        System.out.println("\nBatchDecoder: All files decoded\n");
        Timer.dumpAll(context);
	decoder.showSummary();
    }



    /**
     * Gets the set of lines from the file
     *
     * @param file the name of the file 
     */
    List getLines(String file) throws IOException {
	List list = new ArrayList();
	BufferedReader reader 
	    = new BufferedReader(new FileReader(batchFile));

	String line = null;

	while ((line = reader.readLine()) != null) {
	    list.add(line);
	}
	reader.close();

	if (totalBatches > 1) {
	    int linesPerBatch = list.size() / totalBatches;
	    if (linesPerBatch < 1) {
		linesPerBatch = 1;
	    }
	    if (whichBatch >= totalBatches) {
		whichBatch = totalBatches - 1;
	    }
	    int startLine = whichBatch * linesPerBatch;
	    System.out.print("Startline " + startLine + " " );
	    //
	    // last batch needs to get all remaining lines
	    if (whichBatch == (totalBatches - 1)) {
		list = list.subList(startLine, list.size());
	    } else {
		list = list.subList(startLine, startLine +
			linesPerBatch);
	    }
	}
	    System.out.println(" size " + list.size());
	return list;
    }


    /**
     * Decodes the given file.
     *
     * @param file the file to decode
     * @param ref the reference string (or null if not available)
     */
    public void decodeFile(String file, String ref) throws IOException {

        System.out.println("\nDecoding: " + file);

	InputStream is = new FileInputStream(file);

	if (inputDataType.equals("audio")) {
	    ((StreamAudioSource) dataSource).setInputStream(is, file);
	} else if (inputDataType.equals("cepstrum")) {
	    ((StreamCepstrumSource) dataSource).setInputStream(is);
	}

        // usually 25 features in one audio frame
        // but it doesn't really matter what this number is
        decoder.decode(ref);
    }



    /**
     * Returns only the file name of the given full file path.
     * For example, "/usr/java/bin/javac" will return "javac".
     *
     * @return the file name of the given full file path
     */
    private static String getFilename(String fullPath) {
        int lastSlash = fullPath.lastIndexOf(File.separatorChar);
        return fullPath.substring(lastSlash+1);
    }


    /**
     * Main method of this BatchDecoder.
     *
     * @param argv argv[0] : SphinxProperties file
     *             argv[1] : a file listing all the audio files to decode
     */
    public static void main(String[] argv) {

        if (argv.length < 2) {
            System.out.println
                ("Usage: BatchDecoder propertiesFile batchFile");
            System.exit(1);
        }

        String context = "batch";
        String propertiesFile = argv[0];
        String batchFile = argv[1];
        String pwd = System.getProperty("user.dir");

        try {
            SphinxProperties.initContext
                (context, new URL("file://" + pwd + 
                                  File.separatorChar + propertiesFile));

            BatchDecoder decoder = new BatchDecoder(context, batchFile);
            decoder.decode();

        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
