
public class Main {

	public static void main(String[] args) {
		TSVMapper tsv = new TSVMapper();
		tsv.writeSV(tsv.createEntries("pledari_sutsilvan_komplett.txt"), "\t", "output.tab");
//		tsv.writeSV(tsv.createEntries("test.txt"), "\t", "output.tab");
	}

}
