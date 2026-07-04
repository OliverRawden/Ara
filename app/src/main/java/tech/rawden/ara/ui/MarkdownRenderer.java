package tech.rawden.ara.ui;

import com.vladsch.flexmark.ast.BlockQuote;
import com.vladsch.flexmark.ast.BulletList;
import com.vladsch.flexmark.ast.Code;
import com.vladsch.flexmark.ast.Emphasis;
import com.vladsch.flexmark.ast.FencedCodeBlock;
import com.vladsch.flexmark.ast.HardLineBreak;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.Image;
import com.vladsch.flexmark.ast.IndentedCodeBlock;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.ast.ListItem;
import com.vladsch.flexmark.ast.OrderedList;
import com.vladsch.flexmark.ast.Paragraph;
import com.vladsch.flexmark.ast.SoftLineBreak;
import com.vladsch.flexmark.ast.StrongEmphasis;
import com.vladsch.flexmark.ast.ThematicBreak;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.Strikethrough;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListItem;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;

import javafx.beans.value.ObservableDoubleValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.Cursor;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;

/**
 * Renders assistant/tool markdown to JavaFX nodes using flexmark (GFM tables, fenced code, task lists).
 */
public final class MarkdownRenderer {

    private static final Font BODY_FONT = Font.font("Inter", 14);
    private static final Font INLINE_CODE_FONT = Font.font("Menlo", 13);

    private static final Parser PARSER;

    static {
        var options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, List.of(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                AutolinkExtension.create(),
                TaskListExtension.create()));
        PARSER = Parser.builder(options).build();
    }

    private MarkdownRenderer() {}

    public static VBox render(String markdown) {
        return render(markdown, null);
    }

    public static VBox render(String markdown, ObservableDoubleValue maxWidth) {
        var container = new VBox(2);
        container.getStyleClass().add("ara-message-assistant");

        if (markdown == null || markdown.isBlank()) {
            var empty = new Text(markdown == null ? "" : markdown);
            empty.setFont(BODY_FONT);
            container.getChildren().add(new TextFlow(empty));
            return container;
        }

        if (maxWidth != null) {
            container.maxWidthProperty().bind(maxWidth);
        }

        var document = PARSER.parse(markdown);
        var visitor = new FxVisitor(container, maxWidth);
        visitor.renderChildren(document);
        return container;
    }

    private static final class FxVisitor {

        private final VBox root;
        private final ObservableDoubleValue maxWidth;

        FxVisitor(VBox root, ObservableDoubleValue maxWidth) {
            this.root = root;
            this.maxWidth = maxWidth;
        }

        void renderChildren(Node parent) {
            for (var child = parent.getFirstChild(); child != null; child = child.getNext()) {
                renderBlock(child);
            }
        }

        private void renderBlock(Node node) {
            switch (node) {
                case Heading heading -> root.getChildren().add(renderHeading(heading));
                case Paragraph paragraph -> root.getChildren().add(renderParagraph(paragraph));
                case FencedCodeBlock fenced -> root.getChildren().add(renderFencedCode(fenced));
                case IndentedCodeBlock indented -> root.getChildren().add(renderIndentedCode(indented));
                case BulletList list -> root.getChildren().add(renderBulletList(list, 0));
                case OrderedList list -> root.getChildren().add(renderOrderedList(list, 0));
                case BlockQuote quote -> root.getChildren().add(renderBlockQuote(quote));
                case ThematicBreak ignored -> root.getChildren().add(renderThematicBreak());
                case com.vladsch.flexmark.ext.tables.TableBlock table ->
                        root.getChildren().add(renderTable(table));
                default -> renderChildren(node);
            }
        }

        private Region renderHeading(Heading heading) {
            var text = collectText(heading);
            var label = new Text(text);
            label.getStyleClass().add("ara-md-h" + heading.getLevel());
            label.setFont(headingFont(heading.getLevel()));
            label.wrappingWidthProperty().bind(wrapWidth());

            var flow = new TextFlow(label);
            flow.getStyleClass().add("ara-md-heading");
            flow.setPadding(headingPadding(heading.getLevel()));
            return flow;
        }

        private TextFlow renderParagraph(Paragraph paragraph) {
            var flow = new TextFlow();
            flow.setPadding(new Insets(4, 0, 4, 0));
            appendInlines(flow, paragraph);
            return flow;
        }

        private VBox renderFencedCode(FencedCodeBlock block) {
            var info = block.getInfo().toString().trim();
            var lang = info;
            var space = info.indexOf(' ');
            if (space > 0) {
                lang = info.substring(0, space);
            }
            var code = block.getContentChars().toString();
            if (code.endsWith("\n")) {
                code = code.substring(0, code.length() - 1);
            }
            return createCodeBlock(code, lang);
        }

        private VBox renderIndentedCode(IndentedCodeBlock block) {
            var code = block.getContentChars().toString();
            if (code.endsWith("\n")) {
                code = code.substring(0, code.length() - 1);
            }
            return createCodeBlock(code, "");
        }

        private VBox createCodeBlock(String code, String language) {
            var codeBox = new VBox(2);
            for (var line : code.split("\n", -1)) {
                var lineFlow = new TextFlow();
                lineFlow.getChildren().addAll(CodeSyntaxHighlighter.highlight(line, language));
                codeBox.getChildren().add(lineFlow);
            }

            var langLabel = new Text(language.isBlank() ? "code" : language);
            langLabel.setFont(Font.font("Inter", FontWeight.SEMI_BOLD, 11));
            langLabel.getStyleClass().add("ara-md-code-lang");

            var copyIcon = new FontIcon("mdi2c-content-copy");
            copyIcon.setIconSize(12);
            var copyBtn = new Button("", copyIcon);
            copyBtn.getStyleClass().add("ara-code-copy-btn");
            copyBtn.setOnAction(e -> {
                var cc = new ClipboardContent();
                cc.putString(code);
                Clipboard.getSystemClipboard().setContent(cc);
            });

            var header = new HBox(langLabel, copyBtn);
            header.setAlignment(Pos.CENTER_RIGHT);
            HBox.setHgrow(langLabel, Priority.ALWAYS);
            header.setPadding(new Insets(0, 0, 6, 0));

            var scroll = new javafx.scene.control.ScrollPane(codeBox);
            scroll.setFitToHeight(true);
            scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scroll.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
            scroll.getStyleClass().add("ara-md-code-scroll");
            scroll.maxWidthProperty().bind(wrapWidth().subtract(28));

            var body = new VBox(6, header, scroll);
            body.setPadding(new Insets(10, 12, 10, 12));
            body.getStyleClass().add("ara-code-block");
            VBox.setMargin(body, new Insets(6, 0, 6, 0));
            body.maxWidthProperty().bind(wrapWidth());
            return body;
        }

        private VBox renderBulletList(BulletList list, int depth) {
            var box = new VBox(4);
            box.setPadding(new Insets(2, 0, 2, depth * 16));
            int index = 0;
            for (var item = list.getFirstChild(); item != null; item = item.getNext()) {
                if (item instanceof ListItem listItem) {
                    box.getChildren().add(renderListItem(listItem, depth, null, index++));
                }
            }
            return box;
        }

        private VBox renderOrderedList(OrderedList list, int depth) {
            var box = new VBox(4);
            box.setPadding(new Insets(2, 0, 2, depth * 16));
            int number = list.getStartNumber();
            for (var item = list.getFirstChild(); item != null; item = item.getNext()) {
                if (item instanceof ListItem listItem) {
                    box.getChildren().add(renderListItem(listItem, depth, number++, 0));
                }
            }
            return box;
        }

        private Region renderListItem(ListItem item, int depth, Integer number, int bulletIndex) {
            var content = new VBox(2);
            String markerText;
            if (item instanceof TaskListItem taskItem) {
                markerText = taskItem.isItemDoneMarker() ? "\u2611  " : "\u2610  ";
            } else if (number != null) {
                markerText = number + ".  ";
            } else {
                markerText = "  \u2022  ";
            }
            var marker = new Text(markerText);
            marker.setFont(BODY_FONT);

            var firstParagraph = true;
            for (var child = item.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof Paragraph paragraph && firstParagraph) {
                    var flow = new TextFlow();
                    flow.getChildren().add(marker);
                    appendInlines(flow, paragraph);
                    content.getChildren().add(flow);
                    firstParagraph = false;
                } else if (child instanceof BulletList nested) {
                    content.getChildren().add(renderBulletList(nested, depth + 1));
                } else if (child instanceof OrderedList nested) {
                    content.getChildren().add(renderOrderedList(nested, depth + 1));
                } else if (child instanceof Paragraph paragraph) {
                    content.getChildren().add(renderParagraph(paragraph));
                } else if (child instanceof FencedCodeBlock fenced) {
                    content.getChildren().add(renderFencedCode(fenced));
                } else {
                    renderBlock(child);
                }
            }

            if (content.getChildren().isEmpty()) {
                var flow = new TextFlow(marker, plainText(collectText(item)));
                content.getChildren().add(flow);
            }

            return content;
        }

        private VBox renderBlockQuote(BlockQuote quote) {
            var inner = new VBox(4);
            inner.setPadding(new Insets(8, 12, 8, 12));
            for (var child = quote.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof Paragraph paragraph) {
                    var flow = new TextFlow();
                    appendInlines(flow, paragraph);
                    inner.getChildren().add(flow);
                } else if (child instanceof BulletList list) {
                    inner.getChildren().add(renderBulletList(list, 0));
                } else if (child instanceof OrderedList list) {
                    inner.getChildren().add(renderOrderedList(list, 0));
                } else if (child instanceof Heading heading) {
                    inner.getChildren().add(renderHeading(heading));
                } else {
                    var flow = new TextFlow(plainText(collectText(child)));
                    inner.getChildren().add(flow);
                }
            }
            inner.getStyleClass().add("ara-md-blockquote");
            var wrapper = new VBox(inner);
            wrapper.setPadding(new Insets(4, 0, 4, 0));
            return wrapper;
        }

        private Region renderThematicBreak() {
            var line = new Region();
            line.setPrefHeight(1);
            line.setMaxHeight(1);
            line.getStyleClass().add("ara-md-hr");
            var wrapper = new VBox(line);
            wrapper.setPadding(new Insets(8, 0, 8, 0));
            return wrapper;
        }

        private Region renderTable(com.vladsch.flexmark.ext.tables.TableBlock table) {
            var grid = new GridPane();
            grid.getStyleClass().add("ara-md-table");
            grid.setHgap(0);
            grid.setVgap(0);
            grid.setPadding(new Insets(0));

            int row = 0;
            for (var section = table.getFirstChild(); section != null; section = section.getNext()) {
                if (section instanceof com.vladsch.flexmark.ext.tables.TableHead
                        || section instanceof com.vladsch.flexmark.ext.tables.TableBody) {
                    for (var tableRow = section.getFirstChild(); tableRow != null; tableRow = tableRow.getNext()) {
                        if (tableRow instanceof com.vladsch.flexmark.ext.tables.TableRow rowNode) {
                            int col = 0;
                            int colsInRow = 0;
                            for (var cell = rowNode.getFirstChild(); cell != null; cell = cell.getNext()) {
                                if (cell instanceof com.vladsch.flexmark.ext.tables.TableCell) {
                                    colsInRow++;
                                }
                            }
                            for (var cell = rowNode.getFirstChild(); cell != null; cell = cell.getNext()) {
                                if (cell instanceof com.vladsch.flexmark.ext.tables.TableCell tableCell) {
                                    boolean lastCol = col == colsInRow - 1;
                                    var cellBox = renderTableCell(
                                            tableCell,
                                            section instanceof com.vladsch.flexmark.ext.tables.TableHead,
                                            lastCol);
                                    grid.add(cellBox, col++, row);
                                }
                            }
                            row++;
                        }
                    }
                }
            }

            var scroll = new javafx.scene.control.ScrollPane(grid);
            scroll.setFitToHeight(true);
            scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scroll.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
            scroll.getStyleClass().add("ara-md-table-scroll");
            scroll.maxWidthProperty().bind(wrapWidth());

            var frame = new VBox(scroll);
            frame.getStyleClass().add("ara-md-table-frame");
            frame.maxWidthProperty().bind(wrapWidth());

            var wrapper = new VBox(frame);
            VBox.setMargin(wrapper, new Insets(8, 0, 8, 0));
            return wrapper;
        }

        private VBox renderTableCell(
                com.vladsch.flexmark.ext.tables.TableCell cell, boolean header, boolean lastColumn) {
            var flow = new TextFlow();
            for (var child = cell.getFirstChild(); child != null; child = cell.getNext()) {
                if (child instanceof Paragraph paragraph) {
                    appendInlines(flow, paragraph);
                } else {
                    flow.getChildren().add(plainText(collectText(child)));
                }
            }
            var box = new VBox(flow);
            box.setPadding(new Insets(8, 10, 8, 10));
            box.getStyleClass()
                    .add(header ? "ara-md-table-header" : "ara-md-table-cell");
            if (lastColumn) {
                box.getStyleClass().add("ara-md-table-cell-last");
            }
            return box;
        }

        private void appendInlines(TextFlow flow, Node parent) {
            for (var child = parent.getFirstChild(); child != null; child = child.getNext()) {
                appendInline(flow, child);
            }
        }

        private void appendInline(TextFlow flow, Node node) {
            switch (node) {
                case com.vladsch.flexmark.ast.Text text ->
                        flow.getChildren().add(plainText(text.getChars().toString()));
                case SoftLineBreak ignored -> flow.getChildren().add(plainText(" "));
                case HardLineBreak ignored -> flow.getChildren().add(plainText("\n"));
                case StrongEmphasis strong -> {
                    var t = new Text(collectText(strong));
                    t.setFont(Font.font("Inter", FontWeight.BOLD, 14));
                    flow.getChildren().add(t);
                }
                case Emphasis emphasis -> {
                    var t = new Text(collectText(emphasis));
                    t.setFont(Font.font("Inter", FontPosture.ITALIC, 14));
                    flow.getChildren().add(t);
                }
                case Code code -> {
                    var t = new Text(code.getText().toString());
                    t.setFont(INLINE_CODE_FONT);
                    t.getStyleClass().add("ara-md-inline-code");
                    flow.getChildren().add(t);
                }
                case Strikethrough strike -> {
                    var t = new Text(collectText(strike));
                    t.setFont(BODY_FONT);
                    t.setStrikethrough(true);
                    t.getStyleClass().add("ara-md-strikethrough");
                    flow.getChildren().add(t);
                }
                case Link link -> flow.getChildren().add(renderLink(link));
                case Image image -> flow.getChildren().add(plainText(altOrSrc(image)));
                default -> {
                    if (node.getFirstChild() != null) {
                        appendInlines(flow, node);
                    } else {
                        var text = node.getChars().toString();
                        if (!text.isBlank()) {
                            flow.getChildren().add(plainText(text));
                        }
                    }
                }
            }
        }

        private Text renderLink(Link link) {
            var label = collectText(link);
            if (label.isBlank()) {
                label = link.getUrl().toString();
            }
            var text = new Text(label);
            text.setFont(BODY_FONT);
            text.setUnderline(true);
            text.getStyleClass().add("ara-md-link");
            text.setCursor(Cursor.HAND);
            text.setOnMouseClicked(e -> {
                try {
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(link.getUrl().toString()));
                } catch (Exception ignored) {
                }
            });
            return text;
        }

        private javafx.beans.binding.DoubleBinding wrapWidth() {
            if (maxWidth == null) {
                return javafx.beans.binding.Bindings.createDoubleBinding(() -> 760.0);
            }
            return javafx.beans.binding.Bindings.createDoubleBinding(
                    () -> Math.max(120, maxWidth.get() - 28), maxWidth);
        }
    }

    private static Text plainText(String value) {
        var text = new Text(value);
        text.setFont(BODY_FONT);
        return text;
    }

    private static String collectText(Node node) {
        var sb = new StringBuilder();
        collectTextInto(node, sb);
        return sb.toString();
    }

    private static void collectTextInto(Node node, StringBuilder sb) {
        if (node instanceof com.vladsch.flexmark.ast.Text text) {
            sb.append(text.getChars());
            return;
        }
        for (var child = node.getFirstChild(); child != null; child = child.getNext()) {
            collectTextInto(child, sb);
        }
        if (node.getFirstChild() == null) {
            var chars = node.getChars().toString();
            if (!chars.isBlank()) {
                sb.append(chars);
            }
        }
    }

    private static String altOrSrc(Image image) {
        var alt = image.getText().toString();
        if (!alt.isBlank()) {
            return alt;
        }
        return image.getUrl().toString();
    }

    private static Font headingFont(int level) {
        return switch (level) {
            case 1 -> Font.font("Inter", FontWeight.BOLD, 22);
            case 2 -> Font.font("Inter", FontWeight.BOLD, 19);
            case 3 -> Font.font("Inter", FontWeight.SEMI_BOLD, 17);
            case 4 -> Font.font("Inter", FontWeight.SEMI_BOLD, 15);
            case 5 -> Font.font("Inter", FontWeight.SEMI_BOLD, 14);
            default -> Font.font("Inter", FontWeight.BOLD, 13);
        };
    }

    private static Insets headingPadding(int level) {
        return switch (level) {
            case 1 -> new Insets(12, 0, 6, 0);
            case 2 -> new Insets(10, 0, 4, 0);
            case 3 -> new Insets(8, 0, 4, 0);
            default -> new Insets(6, 0, 2, 0);
        };
    }
}