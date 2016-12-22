package net.xprova.propertylanguage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import net.xprova.propertylanguage.PropertyLanguageParser.AtomContext;
import net.xprova.propertylanguage.PropertyLanguageParser.PropertyContext;

public class PropertyBuilder {

	// these must be the same as their correspondents in grammar:
	public static final String NOT = "~";
	public static final String AND = "&";
	public static final String XOR = "^";
	public static final String OR = "|";
	public static final String EQ = "==";
	public static final String NEQ = "!=";
	public static final String IMPLY = "|->";
	public static final String IMPLY_NEXT = "|=>";
	public static final String LPAREN = "(";
	public static final String AT = "@";
	public static final String HASH = "#";
	public static final String DOUBLE_HASH = "##";
	public static final String ROSE = "$rose";
	public static final String FELL = "$fell";
	public static final String STABLE = "$stable";
	public static final String CHANGED = "$changed";
	public static final String ALWAYS = "$always";
	public static final String ONCE = "$once";
	public static final String NEVER = "$never";
	public static final String EVENTUALLY = "$eventually";
	public static final String UNTIL = "$until";
	public static final String HIGH = "1";
	public static final String LOW = "0";
	public static final String ANY = "$any";
	public static final String ALL = "$all";

	private static void rewriteSyntaticSugar(Property root) {

		// $until(x,y) into $never(x) |-> y

		if (root.name.equals(UNTIL)) {

			Property neverX = Property.build(NEVER).setChild(root.children.get(0));

			Property y = root.children.get(1);

			root.setChild(neverX).addChild(y).name = IMPLY;

		}

		// change (x |=> #n y) into (x |-> #n+1 y)

		if (root.name.equals(IMPLY_NEXT)) {

			root.name = IMPLY;

			root.children.get(1).delay -= 1;

		}

		// change (x |-> y) into (~x | y)

		if (root.name.equals(IMPLY)) {

			Property c1 = root.children.get(0);
			Property c2 = root.children.get(1);

			Property notC1 = Property.build(NOT).setChild(c1);

			root.setChild(notC1).addChild(c2).name = OR;

		}

		// change ($rose(x)) into (~x & #1 x)

		if (root.name.equals(ROSE)) {

			Property c1 = root.children.get(0);

			Property notC1 = Property.build(NOT).setChild(new Property(c1));

			notC1.delay += 2;

			root.setChild(c1).addChild(notC1).name = AND;

		}

		// change ($fell(x)) into (x & #1 ~x)

		if (root.name.equals(FELL)) {

			Property c1 = root.children.get(0);

			Property notC1 = Property.build(NOT).setChild(new Property(c1));

			c1.delay += 1;

			root.setChild(c1).addChild(notC1).name = AND;

		}

		// $stable(x) into ~(x ^ #1 x)

		if (root.name.equals(STABLE)) {

			Property c1 = new Property(root.children.get(0));
			Property c2 = new Property(root.children.get(0));

			c2.delay += 1;

			Property xorN = Property.build(XOR).addChild(c1).addChild(c2);

			root.setChild(xorN).name = NOT;

		}

		// $changed(x) into (x ^ #1 x)

		if (root.name.equals(CHANGED)) {

			Property c1 = new Property(root.children.get(0));
			Property c2 = new Property(root.children.get(0));

			c2.delay += 1;

			root.setChild(c1).addChild(c2).name = XOR;

		}

		// (x == y) into ~(x ^ y)

		if (root.name.equals(EQ)) {

			Property xorNode = new Property(root);

			xorNode.name = XOR;

			root.setChild(xorNode).name = NOT;

		}

		// (x != y) into (x ^ y)

		if (root.name.equals(NEQ)) {

			root.name = XOR;

		}

		// $never(x) into $always(~x)

		if (root.name.equals(NEVER)) {

			Property notChild = Property.build(NOT).setChildren(root.children);

			root.setChild(notChild).name = ALWAYS;

		}

		// $once(x) into ~$always(~x)

		if (root.name.equals(ONCE)) {

			Property notChild = Property.build(NOT).setChildren(root.children);

			Property always = Property.build(ALWAYS).setChild(notChild);

			root.setChild(always).name = NOT;

		}

		for (Property c : root.children)
			rewriteSyntaticSugar(c);

	}

	private static Property parseAST(ParseTree root) throws Exception {

		if (root.getChildCount() == 1) {

			if (root.getPayload() instanceof AtomContext) {

				return Property.build(root.getText());

			} else {

				return parseAST(root.getChild(0));

			}

		}

		if (root.getChildCount() == 2) {

			// NOT

			Property child = parseAST(root.getChild(1));

			return Property.build(root.getChild(0).getText()).setChild(child);

		}

		String c0 = root.getChild(0).getText();
		String c1 = root.getChild(1).getText();

		final String[] funcs = {ROSE, FELL, STABLE, CHANGED, ALWAYS, NEVER, ONCE, ANY, ALL};

		if (Arrays.asList(funcs).contains(c0)) {

			Property child = parseAST(root.getChild(2));

			return Property.build(c0).setChild(child);

		}

		if (AND.equals(c1) || XOR.equals(c1) || OR.equals(c1)) {

			Property result = Property.build(c1);

			for (int i = 0; i < root.getChildCount(); i += 2)
				result.addChild(parseAST(root.getChild(i)));

			return result;

		}

		if (DOUBLE_HASH.equals(c1)) {

			Property result = Property.build(AND);

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

					result.addChild(childNode);

				}

			}

			return result;

		}

		if (EQ.equals(c1) || NEQ.equals(c1) || IMPLY.equals(c1) || IMPLY_NEXT.equals(c1)) {

			Property op1 = parseAST(root.getChild(0));
			Property op2 = parseAST(root.getChild(2));

			return Property.build(c1).addChild(op1).addChild(op2);

		}

		if (c0.equals(LPAREN)) {

			return parseAST(root.getChild(1));

		}

		if (c0.equals(AT)) {

			Property expr = parseAST(root.getChild(2));

			int delay = Integer.valueOf(c1);

			return Property.build(LPAREN).setChild(expr).delay(delay);

		}

		if (c0.equals(HASH)) {

			Property child = parseAST(root.getChild(2));

			int delay = -Integer.valueOf(c1);

			return Property.build(LPAREN).setChild(child).delay(delay);

		}

		if (c0.equals(EVENTUALLY)) {

			if (root.getChildCount() == 6) {

				Property trigger = parseAST(root.getChild(2));

				Property expr = parseAST(root.getChild(4));

				return Property.build(EVENTUALLY).addChild(trigger).addChild(expr);

			} else if (root.getChildCount() == 4) {

				Property expr = parseAST(root.getChild(2));

				return Property.build(EVENTUALLY).addChild(expr);

			}

		}

		if (c0.equals(UNTIL)) {

			Property trigger = parseAST(root.getChild(2));

			Property expr = parseAST(root.getChild(4));

			return Property.build(UNTIL).addChild(trigger).addChild(expr);

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

	public static int expandMultibit(Property root, HashMap<String, Integer> identifiers) throws Exception {

		// this function performs a top-bottom traversal of a property,
		// re-writing and condensing multi-bit expressions into single bit ones

		// for example:
		// (x & y) == 1
		// where x and y are 2-bit nets, is converted to:
		// (x[0] & y[0] == 1) & (x[1] & y[1] == 0)

		if (root.isTerminal()) {

			Integer bitWidth = identifiers.get(root.name);

			if (bitWidth == null) {

				throw new Exception("unrecognized identifier: " + root.name);

			} else {

				return bitWidth;
			}

		} else {

			ArrayList<Integer> childBitWidths = new ArrayList<Integer>();

			for (Property child : root.children)
				childBitWidths.add(expandMultibit(child, identifiers));

			int count = childBitWidths.get(0);

			for (int x : childBitWidths)
				if (x != count)
					throw new Exception("mismatched operand sizes: " + root);

			final String[] groupingOps = { EQ, NEQ, NOT };

			final String[] reductionOps = { ANY, ALL };

			if (Arrays.asList(groupingOps).contains(root.name) && (count > 1)) {

				ArrayList<Property> propArray = new ArrayList<Property>();

				for (int i = 0; i < count; i++)
					propArray.add(Property.slice(root, i));

				root.name = AND;

				root.setChildren(propArray);

				return 1;

			} else if (Arrays.asList(reductionOps).contains(root.name) && (count > 1)) {

				Property innerExpr = root.children.get(0);

				ArrayList<Property> propArray = new ArrayList<Property>();

				for (int i = 0; i < count; i++)
					propArray.add(Property.slice(innerExpr, i));

				root.name = root.name.equals(ALL) ? AND : OR;

				root.setChildren(propArray);

				return 1;

			} else if (Arrays.asList(reductionOps).contains(root.name) && (count == 1)) {

				// special case;" $any(x)" or "$all(x)" where "x" is a single-bit
				// unwrap $any/$all

				Property innerExpr = root.children.get(0);

				root.copyFrom(innerExpr);

				return 1;

			} else {

				return count;

			}

		}

	}

}
