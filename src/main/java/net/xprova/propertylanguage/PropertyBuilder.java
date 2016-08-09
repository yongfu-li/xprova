package net.xprova.propertylanguage;

import java.util.ArrayList;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import net.xprova.propertylanguage.PropertyLanguageParser.AtomContext;
import net.xprova.propertylanguage.PropertyLanguageParser.PropertyContext;

public class PropertyBuilder {

	// these must be the same as their correspondents in grammar:
	private static final String NOT = "~";
	private static final String AND = "&";
	private static final String XOR = "^";
	private static final String OR = "|";
	private static final String EQ = "==";
	private static final String NEQ = "!=";
	private static final String IMPLY = "|->";
	private static final String IMPLY_NEXT = "|=>";
	private static final String LPAREN = "(";
	private static final String AT = "@";
	private static final String HASH = "#";
	private static final String DOUBLE_HASH = "##";
	private static final String ROSE = "$rose";
	private static final String FELL = "$fell";
	private static final String STABLE = "$stable";
	private static final String CHANGED = "$changed";
	private static final String ALWAYS = "$always";

	private static void rewriteSyntaticSugar(Property root) {

		for (Property c : root.children)
			rewriteSyntaticSugar(c);

		// change (x |=> #n y) into (x |-> #n+1 y)

		if (root.name.equals(IMPLY_NEXT)) {

			root.name = IMPLY;

			Property c1 = root.children.get(1);

			c1.delay -= 1;
		}

		// change (x |-> y) into (~x | y)

		if (root.name.equals(IMPLY)) {

			Property c1 = root.children.get(0);

			Property not = Property.build(NOT);

			not.children.add(c1);

			root.children.set(0, not);

			root.name = "|";

		}

		// change ($rose(x)) into (~x & #1 x)

		if (root.name.equals(ROSE)) {

			root.name = AND;

			Property c1 = root.children.get(0);

			Property c2 = Property.build(NOT).child(new Property(c1)).delay(1);

			root.children.clear();

			root.children.add(c1);
			root.children.add(c2);
		}

		// change ($fell(x)) into (x & #1 ~x)

		if (root.name.equals(FELL)) {

			root.name = AND;

			Property c1 = root.children.get(0);

			Property c2 = Property.build(NOT).child(new Property(c1));

			c1.delay += 1;

			root.children.clear();

			root.children.add(c1);
			root.children.add(c2);
		}

		// $stable(x) into ~(x ^ #1 x)

		if (root.name.equals(STABLE)) {

			Property c1 = new Property(root.children.get(0));
			Property c2 = new Property(root.children.get(0));

			c2.delay += 1;

			Property xorN = Property.build(XOR);

			xorN.children.add(c1);
			xorN.children.add(c2);

			root.name = NOT;

			root.children.clear();

			root.children.add(xorN);

		}

		// $changed(x) into (x ^ #1 x)

		if (root.name.equals(CHANGED)) {

			Property c1 = new Property(root.children.get(0));
			Property c2 = new Property(root.children.get(0));

			c2.delay += 1;

			root.name = XOR;

			root.children.clear();

			root.children.add(c1);
			root.children.add(c2);

		}

		// (x == y) into ~(x ^ y)

		if (root.name.equals(EQ)) {

			Property xorNode = new Property(root);

			xorNode.name = "^";

			root.children.clear();

			root.name = "~";

			root.children.add(xorNode);

		}

		// (x != y) into (x ^ y)

		if (root.name.equals(NEQ)) {

			root.name = "^";

		}

	}

	private static Property parseAST(ParseTree root) throws Exception {

		ArrayList<Property> children = new ArrayList<Property>();

		if (root.getChildCount() == 1) {

			if (root.getPayload() instanceof AtomContext) {

				return Property.build(root.getText());

			} else {

				return parseAST(root.getChild(0));

			}

		}

		if (root.getChildCount() == 2) {

			// NOT

			children.add(parseAST(root.getChild(1)));

			return Property.build(root.getChild(0).getText()).children(children);

		}

		String c0 = root.getChild(0).getText();
		String c1 = root.getChild(1).getText();

		if (ROSE.equals(c0) || FELL.equals(c0) || STABLE.equals(c0) || CHANGED.equals(c0) || ALWAYS.equals(c0)) {

			children.add(parseAST(root.getChild(2)));

			return Property.build(c0).children(children);

		}

		if (AND.equals(c1) || XOR.equals(c1) || OR.equals(c1)) {

			for (int i = 0; i < root.getChildCount(); i += 2)
				children.add(parseAST(root.getChild(i)));

			return Property.build(c1).children(children);

		} else if (DOUBLE_HASH.equals(c1)) {

			int cumDelay = 0;

			for (int i = 0; i < root.getChildCount(); i++) {

				ParseTree ci = root.getChild(i);

				if (ci.getPayload() instanceof Token) {

					Token pl = (Token) ci.getPayload();

					if (DOUBLE_HASH.equals(pl.getText())) {

						cumDelay += 1;

					} else {

						// token is NUM

						// mind the (-1): we've incremented cumDelay when
						// we processed the preceding DOUBLE_DASH so this
						// is to make the total increase due to ##n equal
						// to n

						cumDelay += Integer.valueOf(pl.getText()) - 1;

					}

				} else {

					// this is an identifier

					Property childNode = parseAST(ci);

					childNode.delay -= cumDelay;

					children.add(childNode);

				}

			}

			return Property.build(AND).children(children);

		} else if (EQ.equals(c1) || NEQ.equals(c1) || IMPLY.equals(c1) || IMPLY_NEXT.equals(c1)) {

			children.add(parseAST(root.getChild(0)));
			children.add(parseAST(root.getChild(2)));

			return Property.build(c1).children(children);

		} else if (c0.equals(LPAREN)) {

			children.add(parseAST(root.getChild(1)));

			return Property.build(LPAREN).children(children);

		} else if (c0.equals(AT)) {

			children.add(parseAST(root.getChild(2)));

			int delay = Integer.valueOf(c1);

			return Property.build(LPAREN).children(children).delay(delay);

		} else if (c0.equals(HASH)) {

			children.add(parseAST(root.getChild(2)));

			int delay = -Integer.valueOf(c1);

			return Property.build(LPAREN).children(children).delay(delay);

		}

		System.out.println(root.getText());

		throw new Exception("error while traversing property AST");

	}

	public static Property build(String str) throws Exception {

		// step 1: generate property AST

		ANTLRInputStream antlr = new ANTLRInputStream(str);

		PropertyLanguageLexer lexer1 = new PropertyLanguageLexer(antlr);

		CommonTokenStream tokenStream = new CommonTokenStream(lexer1);

		PropertyLanguageParser p1 = new PropertyLanguageParser(tokenStream);

		PropertyContext e = p1.property();

		// step 2: traverse AST to generate expression tree

		Property root = parseAST(e.getChild(0));

		// step 3: process syntactic sugar

		rewriteSyntaticSugar(root);

		// step 4: normalise delays

		root.flattenDelays(0);

		root.addDelayRecur(-root.getMinDelay(0));

		root.groupDelays();

		root.print();

		return root;

	}

}