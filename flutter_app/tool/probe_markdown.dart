// ignore_for_file: avoid_print
import 'package:markdown/markdown.dart' as md;
void main() {
 print(md.markdownToHtml('<h2 id="chapter">HTML title</h2>\n\n<a name="target"></a>\n<p>Text <a href="https://example.com">click</a></p>\n<img src="assets/images/test.png">',extensionSet:md.ExtensionSet.gitHubWeb));
 print(md.markdownToHtml('# title\n\n- [ ] **first**\n- [x] second\n\nreference [^note]\n\n[^note]: explanation',extensionSet:md.ExtensionSet.gitHubWeb));
}
