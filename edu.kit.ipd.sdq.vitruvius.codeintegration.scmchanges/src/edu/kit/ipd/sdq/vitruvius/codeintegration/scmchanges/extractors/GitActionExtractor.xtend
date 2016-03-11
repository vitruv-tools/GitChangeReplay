package edu.kit.ipd.sdq.vitruvius.codeintegration.scmchanges.extractors

import com.github.gumtreediff.actions.ActionGenerator
import com.github.gumtreediff.gen.jdt.JdtTreeGenerator
import com.github.gumtreediff.matchers.Matchers
import edu.kit.ipd.sdq.vitruvius.codeintegration.scmchanges.IScmActionExtractor
import java.util.HashSet
import java.util.NoSuchElementException
import org.apache.log4j.Logger
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.lib.AnyObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevSort
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import java.util.ArrayList
import org.eclipse.jdt.internal.formatter.old.CodeFormatter
import edu.kit.ipd.sdq.vitruvius.codeintegration.scmchanges.ExtractionResult
import org.eclipse.jgit.treewalk.filter.PathFilter

class GitActionExtractor implements IScmActionExtractor<AnyObjectId> {
	
	private static final Logger logger = Logger.getLogger(typeof(GitActionExtractor))
	
	private Repository repository
	
	private JdtTreeGenerator treeGenerator
	
	new(Repository repository) {
		this.repository = repository
		this.treeGenerator = new JdtTreeGenerator();
	}
	
	override extract(AnyObjectId newVersion, AnyObjectId oldVersion) {
		//TODO if commits are not neighbors iterate over each commit-pair between
		logger.info('''Computing changes between git repo versions «newVersion» to «oldVersion»''')
		val reader = repository.newObjectReader()
		val git = new Git(repository)
		val revWalk = new RevWalk(repository)
		try {
			revWalk.markStart(revWalk.parseCommit(newVersion))
			val oldCommit = revWalk.parseCommit(oldVersion)
			
			revWalk.sort(RevSort.COMMIT_TIME_DESC)
			val revsNewToOld = findNewToOld(revWalk, oldCommit)
			val oldToNew = revsNewToOld.sortBy[it.commitTime]
			val commitIterator = oldToNew.iterator
			var fromCommit = commitIterator.next
			var toCommit = commitIterator.next
			val allResults = new ArrayList()
			while (toCommit != null) {
				val newTree = new CanonicalTreeParser
				newTree.reset(reader, toCommit.tree.id)
				val oldTree = new CanonicalTreeParser
				oldTree.reset(reader, fromCommit.tree.id)
				val diff = git.diff
				diff.newTree = newTree
				diff.oldTree = oldTree
				diff.pathFilter = PathFilter.create("*.java")
				val diffs = diff.call
			
				val result = diffs.map[extractActions(it)]
				allResults.add(result)
				
				fromCommit = toCommit
				toCommit = try {commitIterator.next} catch (NoSuchElementException e) {null}
			}
			
			return allResults.flatten

		} finally {
			git.close
			reader.close
		}
		
	}
	
	def findNewToOld(RevWalk revWalk, RevCommit oldCommit) {
		val revsNewToOld = new ArrayList
		for (rev : revWalk) {
			revsNewToOld.add(rev)
			if (rev.equals(oldCommit)) {
				return revsNewToOld
			}
		}
		return revsNewToOld
	}
	
	private def extractActions(DiffEntry entry) {
		val oldObjectLoader = repository.open(entry.oldId.toObjectId)
		val newObjectLoder = repository.open(entry.newId.toObjectId)
		
		val oldTreeContext = treeGenerator.generateFromStream(oldObjectLoader.openStream)
		val newTreeContext = treeGenerator.generateFromStream(newObjectLoder.openStream)
		
		val oldTree = oldTreeContext.root
		val newTree = newTreeContext.root
		
		val matcher = Matchers.instance.getMatcher(oldTree, newTree)
		matcher.match
		
		val mappings = matcher.mappings
		val actionGenerator = new ActionGenerator(oldTree, newTree, mappings)
		actionGenerator.generate
		val result = new ExtractionResult(oldTreeContext, newTreeContext, mappings, actionGenerator.actions)
		return result
	}
	
}