package com.onroad.app.ui.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.onroad.app.databinding.FragmentUserBinding
import com.onroad.app.ui.base.BaseFragment
import javax.inject.Inject

class UserFragment : BaseFragment(), UserContract.View {

    @Inject
    lateinit var presenter: UserContract.Presenter<UserContract.View>;

    //view que contiene los elementos de este fragment
    lateinit var binding: FragmentUserBinding;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);

        baseActivity.activityComponent.inject(this);
        presenter.attachFragment(this, baseActivity);
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        //si es la primera vez que se accede al fragment lo creamos
        if (!::binding.isInitialized) {
            binding = FragmentUserBinding.inflate(layoutInflater, container, false);
        }
        initFragment()

        return binding.root;
    }

    override fun initFragment() {
        binding.apply {
            initialLetter.text = dataManager.usuarioProvider.user!!.firstname!!.take(1)
            document.text = dataManager.usuarioProvider.user!!.email
            name.text = dataManager.usuarioProvider.user!!.firstname + " " + dataManager.usuarioProvider.user!!.lastname

            helmetButton.setOnClickListener {
                baseActivity.showLoading()
            }
        }
    }

    fun isBindingInitialized() = ::binding.isInitialized;

}