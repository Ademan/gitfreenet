package gitfreenet.freenet;

import java.util.Map;

import java.net.URI;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;

import freenet.support.api.HTTPRequest;

import freenet.client.HighLevelSimpleClient;
import freenet.client.async.ClientGetCallback;
import freenet.client.async.ClientGetter;
import freenet.client.async.ClientContext;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.node.RequestStarter;
import freenet.keys.FreenetURI;
import freenet.keys.ClientCHK;
import freenet.node.RequestClient;
import freenet.support.io.ResumeFailedException;

import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;

import freenet.support.MultiValueTable;

import java.io.UnsupportedEncodingException;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;

import java.util.regex.Pattern;

public class RepositoryUpdateProgress extends Toadlet {
	final String updatesPath = "/updates";
	final String updatesRepoPath = "/updates/SomeRepo"; // FIXME
	final Pattern repoMatch = Pattern.compile("(CHK@|SSK@|KSK@)[a-zA-Z0-9~-]{20},[a-zA-Z0-9~-]{20},[a-zA-Z0-9~-]*((%2f[a-zA-Z0-9+-=~,.;:?$&)*)*");

	Plugin plugin;
	public RepositoryUpdateProgress(HighLevelSimpleClient client, Plugin plugin) {
		super(client);
		this.plugin = plugin;
	}

	public void handleMethodGET(URI uri, HTTPRequest req, ToadletContext ctx) {
		MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
		StringBuilder sb = new StringBuilder();

		ByteArrayOutputStream data = new ByteArrayOutputStream();
		PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(data)));

		SortedMap<FreenetURI, RepositoryUpdateTransaction> updates = new TreeMap<FreenetURI, RepositoryUpdateTransaction>(plugin.getUpdates());
		String route = uri.getPath();

		writer.print("<!DOCTYPE html>");
		writer.print("<html>");
		writer.print("<body>");

		if (route.startsWith(updatesRepoPath)) { // FIXME
		} else if (route.startsWith(updatesPath)) {
			writer.print("<h1>Updates</h1>");
			writer.print("<ul>");
			for (Map.Entry<FreenetURI, RepositoryUpdateTransaction> update : updates.entrySet()) {
				FreenetURI updateURI = update.getKey();
				RepositoryUpdateTransaction updateTransaction = update.getValue();
				writer.format("<li><a href=\"%s\">%s</a>", uri.resolve(updateURI.toString()));
				writer.print("<ul>");
				for (RepositoryUpdateTransaction.RepositoryGetCallback callback : updateTransaction.getPendingOperations()) {
					String klass = "in-progress";
					writer.format("<li class=\"%s\">%s</li>", klass, updateTransaction);
				}
				writer.print("</ul>");
				writer.print("</li>");
			}
			writer.print("</ul>");
		} else {
			try {
				ctx.sendReplyHeaders(404, "NOT FOUND", headers, "text/html", 0);
			} catch (ToadletContextClosedException e) {
				// TODO: log
			} catch (IOException e) {
				// TODO: log
			}
			return;
		}

		writer.print("</body>");
		writer.print("</html>");

		try {
			ctx.sendReplyHeaders(200, "OK", headers, "text/html", data.size());
			ctx.writeData(data.toByteArray());
		} catch (ToadletContextClosedException e) {
			// TODO: log
		} catch (IOException e) {
			// TODO: log
		}

	}

	public String path() { return "/status"; }
}
