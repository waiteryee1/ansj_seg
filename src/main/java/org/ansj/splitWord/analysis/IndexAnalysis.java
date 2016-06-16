package org.ansj.splitWord.analysis;

import java.io.Reader;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.ansj.domain.Result;
import org.ansj.domain.Term;
import org.ansj.library.UserDefineLibrary;
import org.ansj.recognition.arrimpl.AsianPersonRecognition;
import org.ansj.recognition.arrimpl.ForeignPersonRecognition;
import org.ansj.recognition.arrimpl.NumRecognition;
import org.ansj.recognition.arrimpl.UserDefineRecognition;
import org.ansj.splitWord.Analysis;
import org.ansj.util.AnsjReader;
import org.ansj.util.Graph;
import org.ansj.util.MyStaticValue;
import org.ansj.util.NameFix;
import org.ansj.util.TermUtil.InsertTermType;
import org.nlpcn.commons.lang.tire.GetWord;
import org.nlpcn.commons.lang.tire.domain.Forest;
import org.nlpcn.commons.lang.util.ObjConver;

/**
 * 用于检索的分词方式
 * 
 * @author ansj
 * 
 */
public class IndexAnalysis extends Analysis {

	protected static final Forest[] DEFAULT_FORESTS = new Forest[] { UserDefineLibrary.FOREST };;

	@Override
	protected List<Term> getResult(final Graph graph) {
		Merger merger = new Merger() {

			@Override
			public List<Term> merger() {
				graph.walkPath();

				// 数字发现
				if (MyStaticValue.isNumRecognition && graph.hasNum) {
					new NumRecognition().recognition(graph.terms);
				}

				// 姓名识别
				if (graph.hasPerson && MyStaticValue.isNameRecognition) {
					// 亚洲人名识别
					new AsianPersonRecognition().recognition(graph.terms);
					graph.walkPathByScore();
					NameFix.nameAmbiguity(graph.terms);
					// 外国人名识别
					new ForeignPersonRecognition().recognition(graph.terms);
					graph.walkPathByScore();
				}

				// 用户自定义词典的识别
				userDefineRecognition(graph, forests);

				return result();
			}

			private void userDefineRecognition(final Graph graph, Forest... forests) {
				new UserDefineRecognition(InsertTermType.SKIP, forests).recognition(graph.terms);
				graph.rmLittlePath();
				graph.walkPathByScore();
			}

			/**
			 * 检索的分词
			 * 
			 * @return
			 */
			private List<Term> result() {

				String temp = null;

				Set<String> set = new HashSet<String>();

				List<Term> result = new LinkedList<Term>();
				int length = graph.terms.length - 1;
				for (int i = 0; i < length; i++) {
					if (graph.terms[i] != null) {
						result.add(graph.terms[i]);
						set.add(graph.terms[i].getName() + graph.terms[i].getOffe());
					}
				}

				LinkedList<Term> last = new LinkedList<Term>();

				Forest[] tempForests = DEFAULT_FORESTS;

				if (forests != null && forests.length > 0) {
					tempForests = forests;
				}

				char[] chars = graph.chars;

				for (Forest forest : tempForests) {
					GetWord word = forest.getWord(chars);
					while ((temp = word.getAllWords()) != null) {
						if (!set.contains(temp + word.offe)) {
							set.add(temp + word.offe);
							last.add(new Term(temp, word.offe, word.getParam(0), ObjConver.getIntValue(word.getParam(1))));
						}
					}
				}

				result.addAll(last);
				
				Collections.sort(result,new Comparator<Term>() {

					@Override
					public int compare(Term o1, Term o2) {
						if(o1.getOffe()==o2.getOffe()){
							return o2.getName().length()-o1.getName().length() ;
						}else{
							return o1.getOffe()-o2.getOffe() ;
						}
					}
				});

				setRealName(graph, result);
				return result;
			}
		};

		return merger.merger();
	}
	
	public IndexAnalysis(Forest... forests) {
		if (forests == null) {
			forests = new Forest[] { UserDefineLibrary.FOREST };
		}
		this.forests = forests;
	}

	public IndexAnalysis(Reader reader, Forest... forests) {
		this.forests = forests;
		super.resetContent(new AnsjReader(reader));
	}

	public static Result parse(String str) {
		return new IndexAnalysis().parseStr(str);
	}

	public static Result parse(String str, Forest... forests) {
		return new IndexAnalysis(forests).parseStr(str);

	}
}
