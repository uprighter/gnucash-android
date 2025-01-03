/*
 * Copyright (c) 2012-2024 Gnucash Android Developers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.export.ofx

import android.content.Context
import android.preference.PreferenceManager
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.StringWriter
import java.io.Writer
import java.sql.Timestamp
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.Exporter
import org.gnucash.android.model.Account
import org.gnucash.android.model.Account.Companion.convertToOfxAccountType
import org.gnucash.android.util.PreferencesHelper
import org.gnucash.android.util.TimestampHelper
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import timber.log.Timber

/**
 * Exports the data in the database in OFX format.
 *
 * @author Ngewi Fet <ngewi.fet@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 */
class OfxExporter(context: Context, params: ExportParams, bookUID: String) :
    Exporter(context, params, bookUID) {
    /**
     * Converts all expenses into OFX XML format and adds them to the XML document.
     *
     * @param accounts List of accounts to export.
     * @param doc DOM document of the OFX expenses.
     * @param parent Parent node for all expenses in report.
     */
    private fun generateOfx(accounts: List<Account>, doc: Document, parent: Element) {
        val transactionUid = doc.createElement(OfxHelper.TAG_TRANSACTION_UID)
        // Unsolicited because the data exported is not as a result of a request.
        transactionUid.appendChild(doc.createTextNode(OfxHelper.UNSOLICITED_TRANSACTION_ID))
        val statementTransactionResponse =
            doc.createElement(OfxHelper.TAG_STATEMENT_TRANSACTION_RESPONSE)
        statementTransactionResponse.appendChild(transactionUid)
        val bankmsgs = doc.createElement(OfxHelper.TAG_BANK_MESSAGES_V1)
        bankmsgs.appendChild(statementTransactionResponse)
        parent.appendChild(bankmsgs)
        val isDoubleEntryEnabled = GnuCashApplication.isDoubleEntryEnabled()
        val nameImbalance = mContext.getString(R.string.imbalance_account_name)
        accounts
            .filter { it.transactionCount > 0 }
            .filter {
                isDoubleEntryEnabled ||
                    // TODO: investigate whether skipping the imbalance accounts makes sense.
                    // Also, using locale-dependant names here is error-prone.
                    it.name?.contains(nameImbalance) == false
            }
            .forEach { account ->
                // Add account details (transactions) to the XML document.
                writeAccount(account, doc, statementTransactionResponse, mExportParams.exportStartTime)
                // Mark as exported.
                mAccountsDbAdapter.markAsExported(account.uID)
            }
    }

    /**
     * Generate OFX export file from the transactions in the database.
     *
     * @param accounts List of accounts to export.
     * @return String containing OFX export.
     * @throws ExporterException if an XML builder could not be created.
     */
    @Throws(ExporterException::class)
    private fun generateOfxExport(accounts: List<Account>): String {
        val document = makeDocBuilder().newDocument()
        val root = document.createElement("OFX")
        val pi = document.createProcessingInstruction("OFX", OfxHelper.OFX_HEADER)
        document.appendChild(pi)
        document.appendChild(root)
        generateOfx(accounts, document, root)
        val useXmlHeader = PreferenceManager.getDefaultSharedPreferences(mContext)
            .getBoolean(mContext.getString(R.string.key_xml_ofx_header), false)
        PreferencesHelper.setLastExportTime(TimestampHelper.getTimestampFromNow())
        val stringWriter = StringWriter()
        if (useXmlHeader) {
            write(document, stringWriter, false)
            return stringWriter.toString()
        }
        // If we want SGML OFX headers, write first to string and then prepend header.
        val ofxNode = document.getElementsByTagName("OFX").item(0)
        write(ofxNode, stringWriter, true)
        return OfxHelper.OFX_SGML_HEADER + "\n" + stringWriter
    }

    @Throws(ExporterException::class)
    private fun makeDocBuilder(): DocumentBuilder {
        val docFactory = DocumentBuilderFactory.newInstance()
        try {
            return docFactory.newDocumentBuilder()
        } catch (e: ParserConfigurationException) {
            throw ExporterException(mExportParams, e)
        }
    }

    @Throws(ExporterException::class)
    override fun generateExport(): List<String> {
        val accounts = mAccountsDbAdapter.getExportableAccounts(mExportParams.exportStartTime)
        if (accounts.isEmpty()) { // Nothing to export, so no files generated
            close()
            return listOf()
        }
        var writer: BufferedWriter? = null
        try {
            val file = File(exportCacheFilePath)
            writer = BufferedWriter(OutputStreamWriter(FileOutputStream(file), "UTF-8"))
            writer.write(generateOfxExport(accounts))
            close()
        } catch (e: IOException) {
            throw ExporterException(mExportParams, e)
        } finally {
            if (writer != null) {
                try {
                    writer.close()
                } catch (e: IOException) {
                    throw ExporterException(mExportParams, e)
                }
            }
        }
        return listOf(exportCacheFilePath)
    }

    /**
     * Writes out the document held in `node` to `outputWriter`
     *
     * @param node Node, containing the OFX document structure. Usually the parent node.
     * @param outputWriter Writer to use in writing the file to stream.
     * @param omitXmlDeclaration Flag which causes the XML declaration to be omitted.
     */
    private fun write(node: Node, outputWriter: Writer, omitXmlDeclaration: Boolean) {
        try {
            val transformerFactory = TransformerFactory.newInstance()
            val transformer = transformerFactory.newTransformer()
            val source = DOMSource(node)
            val result = StreamResult(outputWriter)
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            if (omitXmlDeclaration) {
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            }
            transformer.transform(source, result)
        } catch (e: TransformerException) {
            Timber.e(e)
            throw ExporterException(mExportParams, e)
        }
    }

    /**
     * Returns the MIME type for this exporter.
     *
     * @return MIME type as string
     */
    override fun getExportMimeType(): String {
        return "text/xml"
    }

    /**
     * Converts this account's transactions into XML and adds them to the DOM document
     *
     * @param account The account.
     * @param doc             XML DOM document for the OFX data
     * @param parent          Parent node to which to add this account's transactions in XML
     * @param exportStartTime Time from which to export transactions which are created/modified after
     */
    private fun writeAccount(
        account: Account,
        doc: Document,
        parent: Element,
        exportStartTime: Timestamp?
    ) {
        val currency = doc.createElement(OfxHelper.TAG_CURRENCY_DEF)
        currency.appendChild(doc.createTextNode(account.commodity.currencyCode))

        //================= BEGIN BANK ACCOUNT INFO (BANKACCTFROM) =================================
        val bankId = doc.createElement(OfxHelper.TAG_BANK_ID)
        bankId.appendChild(doc.createTextNode(OfxHelper.APP_ID))
        val acctId = doc.createElement(OfxHelper.TAG_ACCOUNT_ID)
        acctId.appendChild(doc.createTextNode(account.uID))
        val accttype = doc.createElement(OfxHelper.TAG_ACCOUNT_TYPE)
        val ofxAccountType = convertToOfxAccountType(account.accountType).toString()
        accttype.appendChild(doc.createTextNode(ofxAccountType))
        val bankFrom = doc.createElement(OfxHelper.TAG_BANK_ACCOUNT_FROM)
        bankFrom.appendChild(bankId)
        bankFrom.appendChild(acctId)
        bankFrom.appendChild(accttype)

        //================= END BANK ACCOUNT INFO ============================================


        //================= BEGIN ACCOUNT BALANCE INFO =================================
        val balance = account.balance.toPlainString()
        val formattedCurrentTimeString = OfxHelper.getFormattedCurrentTime()
        val balanceAmount = doc.createElement(OfxHelper.TAG_BALANCE_AMOUNT)
        balanceAmount.appendChild(doc.createTextNode(balance))
        val dtasof = doc.createElement(OfxHelper.TAG_DATE_AS_OF)
        dtasof.appendChild(doc.createTextNode(formattedCurrentTimeString))
        val ledgerBalance = doc.createElement(OfxHelper.TAG_LEDGER_BALANCE)
        ledgerBalance.appendChild(balanceAmount)
        ledgerBalance.appendChild(dtasof)

        //================= END ACCOUNT BALANCE INFO =================================


        //================= BEGIN TIME PERIOD INFO =================================
        val dtstart = doc.createElement(OfxHelper.TAG_DATE_START)
        dtstart.appendChild(doc.createTextNode(formattedCurrentTimeString))
        val dtend = doc.createElement(OfxHelper.TAG_DATE_END)
        dtend.appendChild(doc.createTextNode(formattedCurrentTimeString))

        //================= END TIME PERIOD INFO =================================


        //================= BEGIN TRANSACTIONS LIST =================================
        val bankTransactionsList = doc.createElement(OfxHelper.TAG_BANK_TRANSACTION_LIST)
        bankTransactionsList.appendChild(dtstart)
        bankTransactionsList.appendChild(dtend)
        for (transaction in account.transactions) {
            if (transaction.modifiedTimestamp.before(exportStartTime)) continue
            bankTransactionsList.appendChild(transaction.toOFX(doc, account.uID!!))
        }
        //================= END TRANSACTIONS LIST =================================
        val statementTransactions = doc.createElement(OfxHelper.TAG_STATEMENT_TRANSACTIONS)
        statementTransactions.appendChild(currency)
        statementTransactions.appendChild(bankFrom)
        statementTransactions.appendChild(bankTransactionsList)
        statementTransactions.appendChild(ledgerBalance)
        parent.appendChild(statementTransactions)
    }
}
